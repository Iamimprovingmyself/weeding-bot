package com.weddingbot;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@SpringBootApplication(exclude = R2dbcAutoConfiguration.class)
@RestController
public class WeddingBot extends TelegramLongPollingBot {

    private static final CopyOnWriteArrayList<ResponseData> responses = new CopyOnWriteArrayList<>();
    private static final String BOT_TOKEN = "8137314738:AAHkuw_YmTatwT5Hfr0myPYyaK5WI1-E8No";
    private static final String BOT_USERNAME = "@WeedingDianaBot";
    private static final String ADMIN_CHAT_ID = "7886639302";


    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/rsvp")
                        .allowedOrigins("*")
                        .allowedMethods("POST")
                        .allowedHeaders("*");
            }
        };
    }

    @PostMapping("/rsvp")
    public String handleRsvp(@RequestParam Map<String, String> formData) {
        ResponseData response = new ResponseData(
                formData.get("Имя"),
                formData.get("Придет"),
                formData.get("+1"),
                formData.get("Предпочтения в алкоголе"),
                formData.get("Пожелания"),
                new Date()
        );

        responses.add(response);
        sendNotificationToAdmin(response);
        return "Спасибо за ответ! Мы с нетерпением ждём встречи!";
    }

    private void sendNotificationToAdmin(ResponseData response) {
        String message = String.format(
                "🎉 Новый ответ!%nИмя: %s%nПрисутствие: %s%n+1: %s%nАлкоголь: %s%nПожелания: %s%nДата заполнения: %s",
                response.getName(),
                response.getAttendance(),
                response.getPlusOne(),
                response.getAlcoholPreference(),
                response.getWishes(),
                response.getFormattedDate() // Используем форматированную дату
        );

        sendTextMessage(Long.parseLong(ADMIN_CHAT_ID), message);
    }


    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (text) {
                case "/start":
                    handleStartCommand(chatId);
                    break;
                case "/report":
                    if (ADMIN_CHAT_ID.equals(String.valueOf(chatId))) {
                        generateReport(chatId); // Excel
                    }
                    break;
                case "/report_text":
                    if (ADMIN_CHAT_ID.equals(String.valueOf(chatId))) {
                        generateTextReport(chatId); // Новый текстовый отчет
                    }
                    break;
                default:
                    sendTextMessage(chatId, "Неизвестная команда 😕");
            }
        }
    }

    private void handleStartCommand(long chatId) {
        String welcomeMessage = "🎉 Добро пожаловать на нашу свадьбу!\n\n" +
                "Используйте форму на сайте для подтверждения участия.\n" +
                "Администраторы могут использовать /report для получения отчета";

        sendTextMessage(chatId, welcomeMessage);
    }

    private void generateTextReport(long chatId) {
        if (responses.isEmpty()) {
            sendTextMessage(chatId, "Отчет пуст. Пока нет ни одного ответа.");
            return;
        }

        StringBuilder report = new StringBuilder("📋 *Список гостей:*\n\n");

        int count = 1;
        for (ResponseData response : responses) {
            report.append(String.format(
                    "*%d.* %s\n" +
                            "🗓 Дата: _%s_\n" +
                            "👤 Присутствие: %s\n" +
                            "➕ +1: %s\n" +
                            "🍷 Алкоголь: %s\n" +
                            "📝 Пожелания: %s\n\n",
                    count++,
                    escapeMarkdown(response.getName()),
                    response.getFormattedDate(),
                    escapeMarkdown(response.getAttendance()),
                    escapeMarkdown(response.getPlusOne()),
                    escapeMarkdown(response.getAlcoholPreference()),
                    escapeMarkdown(response.getWishes())
            ));
        }

        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(report.toString());
            message.setParseMode("Markdown");
            execute(message);
        } catch (TelegramApiException e) {
            handleError(chatId, "Ошибка при отправке текстового отчета: " + e.getMessage());
        }
    }

    private String escapeMarkdown(String input) {
        return input == null ? "" : input.replace("_", "\\_").replace("*", "\\*")
                .replace("[", "\\[").replace("`", "\\`");
    }

    private void generateReport(long chatId) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Responses");

            // Заголовки
            String[] headers = {"Дата", "Имя", "Присутствие", "+1", "Алкоголь", "Пожелания"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Данные
            int rowNum = 1;
            for (ResponseData response : responses) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(response.getDate().toString());
                row.createCell(1).setCellValue(response.getName());
                row.createCell(2).setCellValue(response.getAttendance());
                row.createCell(3).setCellValue(response.getPlusOne());
                row.createCell(4).setCellValue(response.getAlcoholPreference());
                row.createCell(5).setCellValue(response.getWishes());
            }

            // Сохранение файла
            File reportFile = File.createTempFile("report_", ".xlsx");
            try (FileOutputStream fos = new FileOutputStream(reportFile)) {
                workbook.write(fos);
            }

            sendDocument(chatId, reportFile);
        } catch (IOException e) {
            handleError(chatId, "Ошибка генерации отчета: " + e.getMessage());
        }
    }

    private void sendDocument(long chatId, File file) {
        try {
            execute(new SendDocument(String.valueOf(chatId), new InputFile(file)));
        } catch (TelegramApiException e) {
            handleError(chatId, "Ошибка отправки файла: " + e.getMessage());
        }
    }

    private void sendTextMessage(long chatId, String text) {
        try {
            execute(new SendMessage(String.valueOf(chatId), text));
        } catch (TelegramApiException e) {
            System.err.println("Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    private void handleError(long chatId, String error) {
        System.err.println(error);
        sendTextMessage(chatId, "❌ " + error);
    }

    public static void main(String[] args) {
        SpringApplication.run(WeddingBot.class, args);
        try {
            new TelegramBotsApi(DefaultBotSession.class).registerBot(new WeddingBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    static class ResponseData {
        private final String name;
        private final String attendance;
        private final String plusOne;
        private final String alcoholPreference;
        private final String wishes;
        private final Date date;


        public ResponseData(String name, String attendance, String plusOne,
                            String alcoholPreference, String wishes, Date date) {
            this.name = name;
            this.attendance = attendance;
            this.plusOne = plusOne;
            this.alcoholPreference = alcoholPreference;
            this.wishes = wishes;
            this.date = date;
        }

         public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            Date adjustedDate = new Date(date.getTime() + 3 * 60 * 60 * 1000); // +3 часа
            return sdf.format(adjustedDate);
        }


        // Геттеры
        public String getName() { return name; }
        public String getAttendance() { return attendance; }
        public String getPlusOne() { return plusOne; }
        public String getAlcoholPreference() { return alcoholPreference; }
        public String getWishes() { return wishes; }
        public Date getDate() { return date; }
    }
}
