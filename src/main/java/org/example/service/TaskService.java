package org.example.service;

import org.apache.pdfbox.Loader;
import org.example.entity.CpItem;
import org.example.entity.EntityTask;
import org.example.repository.CpItemRepository;
import org.example.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import static org.example.entity.EntityTask.TaskStatus.COMPLETED;
import static org.example.entity.EntityTask.TaskStatus.*;
@Service
public class TaskService {

    @Autowired
    private TaskRepository repository;
    @Autowired
    private CpItemRepository cpItemRepository;
    private static final String ERROR_CORRUPTED_FILE = "Файл поврежден или не является КП. Отсутствуют табличные данные.";
    private static final String ERROR_NO_TEXT_LAYER = "Не удалось извлечь данные. Файл содержит только изображение без текстового слоя.";
    private static final String ERROR_PARSE_FAILED = "Не удалось извлечь данные из файла.";


  public UUID createFile(MultipartFile fileName){
        EntityTask task = new EntityTask();
        task.setFile_name(fileName.getOriginalFilename());
        task.setStatus(PENDING);
        task.setCreated_at(LocalDateTime.now());
        task = repository.save(task);
        asyncFile(task.getId(), fileName);
        return task.getId();
    }

    @Async
    public void asyncFile(UUID id, MultipartFile fileName){
        Optional<EntityTask> task =repository.findById(id);
        if (task.isPresent()) {
            EntityTask entityTask = task.get();
                entityTask.setStatus(PROCESSING);
            repository.save(entityTask);
        }
        try {
            byte[] fileBytes = fileName.getBytes();
            String originfile = fileName.getOriginalFilename();
            if(originfile != null && originfile.endsWith(".zip")){
                ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileBytes));
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        byte[] content = zis.readAllBytes();

                        if (entryName.endsWith(".xlsx") || entryName.endsWith(".xls")) {

                            parseExcelFile(content, id);

                        } else if (entryName.endsWith(".pdf")) {
                            String text = extractTextFromPdf(content, id);
                            String aiResult = callGigaChat(text);
                            saveAiResultToDatabase(aiResult, id);

                        } else if (entryName.endsWith(".docx")) {
                            String text = extractTextFromDocx(content);
                            String aiResult = callGigaChat(text);
                            saveAiResultToDatabase(aiResult, id);
                        }
                    }
                    zis.closeEntry();
                }
                zis.close();
            }
            Optional<EntityTask> taskOptional = repository.findById(id);
            if (taskOptional.isPresent()) {
                EntityTask entityTask = taskOptional.get();
                entityTask.setStatus(COMPLETED);
                repository.save(entityTask);
            }

        } catch (Exception e) {
            Optional<EntityTask> taskOptional = repository.findById(id);
            if (taskOptional.isPresent()) {
                EntityTask entityTask = taskOptional.get();
                entityTask.setStatus(FAILED);
                entityTask.setError(e.getMessage());
                repository.save(entityTask);
            }
        }
    }
    private void parseExcelFile(byte[] excelBytes, UUID taskId) throws IOException {
        Workbook workbook = null;
        try {
            workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes));
        } catch (Exception e) {
            workbook = new HSSFWorkbook(new ByteArrayInputStream(excelBytes));
        }

        Sheet sheet = workbook.getSheetAt(0);

        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue;
            Cell supplierCell = row.getCell(0);
            Cell productCell = row.getCell(1);
            Cell priceCell = row.getCell(2);

            if (supplierCell != null && productCell != null && priceCell != null) {
                CpItem item = new CpItem();
                item.setTaskId(taskId);
                item.setSupplierName(getCellValueAsString(supplierCell));
                item.setProductName(getCellValueAsString(productCell));
                item.setPrice(getCellValueAsBigDecimal(priceCell));

                cpItemRepository.save(item);
            }
        }

        workbook.close();
        System.out.println("Excel файл обработан. Сохранено товаров для задачи: " + taskId);
    }

    private String getCellValueAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            default:
                return "";
        }
    }

    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                try {
                    return new BigDecimal(cell.getStringCellValue());
                } catch (NumberFormatException e) {
                    return BigDecimal.ZERO;
                }
            default:
                return BigDecimal.ZERO;
        }
    }
    private String extractTextFromPdf(byte[] pdfBytes, UUID taskId) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text == null || text.trim().length() < 50) {
                updateTaskError(taskId, FAILED, ERROR_NO_TEXT_LAYER);
                throw new IOException(ERROR_NO_TEXT_LAYER);
            }

            return text;
        } catch (IOException e) {
            updateTaskError(taskId, FAILED, ERROR_CORRUPTED_FILE);
            throw new IOException(ERROR_CORRUPTED_FILE, e);
        }
    }
    private void updateTaskError(UUID taskId, EntityTask.TaskStatus status, String errorMessage) {
        Optional<EntityTask> taskOpt = repository.findById(taskId);
        if (taskOpt.isPresent()) {
            EntityTask task = taskOpt.get();
            task.setStatus(status);
            task.setError(errorMessage);
            repository.save(task);
        }
    }

    private String extractTextFromDocx(byte[] docxBytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
            return text.toString();
        }
    }
    private String callGigaChat(String text) {
        String prompt = """
            Ты помогаешь извлекать данные из коммерческих предложений.
            Извлеки из текста:
            1. Название поставщика
            2. Все товары с ценами
            
            Верни ответ ТОЛЬКО в формате JSON:
            {
              "supplier": "название поставщика",
              "items": [
                {"name": "название товара", "price": 123.45}
              ]
            }
            
            Текст для обработки:
            """ + text;

        try {
            HttpClient client = HttpClient.newHttpClient();
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "GigaChat");

            JsonArray messages = new JsonArray();
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            messages.add(message);
            requestBody.add("messages", messages);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://gigachat.devices.sberbank.ru/api/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + getGigaChatToken())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            Gson gson = new Gson();
            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
            String aiResponse = responseJson
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content")
                    .getAsString();

            return aiResponse;

        } catch (Exception e) {
            System.err.println("Ошибка при вызове GigaChat: " + e.getMessage());
            return "{\"supplier\":\"Ошибка вызова AI\",\"items\":[]}";
        }
    }

    private String getGigaChatToken() {
        // TODO: Получить реальный токен из настроек или переменных окружения
        return "TOKEN";
    }

    private void saveAiResultToDatabase(String aiJson, UUID taskId) {
        try {
            Gson gson = new Gson();
            JsonObject json = gson.fromJson(aiJson, JsonObject.class);
            String supplier = json.has("supplier") ? json.get("supplier").getAsString() : "Неизвестный поставщик";

            JsonArray items = json.has("items") ? json.getAsJsonArray("items") : new JsonArray();

            for (int i = 0; i < items.size(); i++) {
                JsonObject itemJson = items.get(i).getAsJsonObject();

                CpItem item = new CpItem();
                item.setTaskId(taskId);
                item.setSupplierName(supplier);
                item.setProductName(itemJson.has("name") ? itemJson.get("name").getAsString() : "Без названия");

                if (itemJson.has("price")) {
                    try {
                        item.setPrice(BigDecimal.valueOf(itemJson.get("price").getAsDouble()));
                    } catch (Exception e) {
                        item.setPrice(BigDecimal.ZERO);
                    }
                } else {
                    item.setPrice(BigDecimal.ZERO);
                }

                cpItemRepository.save(item);
            }

            System.out.println("Сохранено " + items.size() + " товаров из AI для задачи: " + taskId);

        } catch (Exception e) {
            System.err.println("Ошибка при сохранении результата AI: " + e.getMessage());
        }
    }

    public EntityTask getTask(UUID id) {
        return repository.findById(id).orElseThrow(
                () -> new RuntimeException("Задача с ID " + id + " не найдена")
        );
    }

    public static String getErrorParseFailed() {
        return ERROR_PARSE_FAILED;
    }
    public java.util.List<Object[]> findTopSuppliers(String query) {
        return cpItemRepository.findTop5Suppliers(query);
    }

    public java.util.List<CpItem> getItemsByTaskId(UUID taskId) {
        return cpItemRepository.findByTaskId(taskId);
    }
}

