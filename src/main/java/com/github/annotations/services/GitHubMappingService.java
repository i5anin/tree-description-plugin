package com.github.annotations.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;


/**
 * Сервис открытой библиотеки сопоставлений на GitHub
 * Загружает сведения об открытой библиотеке сопоставлений с GitHub
 */
public class GitHubMappingService {
    
    private static final Logger LOG = Logger.getInstance(GitHubMappingService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";
    
    // Список зеркал
    private static final String[] MIRROR_URLS = {
        "https://raw.githubusercontent.com",
        "https://hub.gitmirror.com/raw.githubusercontent.com",
        "https://cdn.statically.io"
    };
    
    private static final String REPO_OWNER = "JaysonX-Tech";
    private static final String REPO_NAME = "tree-description-repository";
    private static final String BRANCH = "main";
    
    private final Gson gson = new Gson();
    
    /**
     * Создаёт HTTP-соединение с учётом прокси
     */
    private HttpURLConnection createProxyConnection(URL url) throws IOException {
        // Прокси выбираем по протоколу URL
        String protocol = url.getProtocol().toLowerCase();
        String proxyHost = null;
        String proxyPort = null;
        
        if ("https".equals(protocol)) {
            proxyHost = System.getProperty("https.proxyHost");
            proxyPort = System.getProperty("https.proxyPort");
        } else if ("http".equals(protocol)) {
            proxyHost = System.getProperty("http.proxyHost");
            proxyPort = System.getProperty("http.proxyPort");
        }
        
        if (proxyHost != null && proxyPort != null) {
            try {
                Proxy proxy = new Proxy(Proxy.Type.HTTP, 
                    new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort)));
                LOG.info("Подключение через прокси " + protocol.toUpperCase() + ": " + proxyHost + ":" + proxyPort + " for " + url);
                return (HttpURLConnection) url.openConnection(proxy);
            } catch (NumberFormatException e) {
                LOG.warn("Неверный порт прокси, подключаемся напрямую: " + e.getMessage());
            }
        } else {
            LOG.info("Прокси " + protocol.toUpperCase() + " не настроен, подключаемся напрямую: " + url);
        }
        
        // Прокси не настроен или настроен неверно — подключаемся напрямую
        return (HttpURLConnection) url.openConnection();
    }
    
    /**
     * Возвращает список содержимого репозитория
     */
    @NotNull
    public List<GitHubMappingFile> getRepositoryContents() {
        List<GitHubMappingFile> files = new ArrayList<>();
        
        try {
            String apiUrl = String.format("%s/repos/%s/%s/contents", GITHUB_API_BASE, REPO_OWNER, REPO_NAME);
            LOG.info("Запрос к GitHub API: " + apiUrl);
            
    

            
            String response = makeGitHubApiRequest(apiUrl);
            
            if (response != null) {
                LOG.info("Длина ответа GitHub API: " + response.length());
                JsonArray contents = gson.fromJson(response, JsonArray.class);
                LOG.info("Разобрано элементов: " + contents.size());
                
                for (JsonElement element : contents) {
                    JsonObject item = element.getAsJsonObject();
                    String path = item.get("path").getAsString();
                    String type = item.get("type").getAsString();
                    LOG.info("Элемент: " + path + ", тип: " + type);
                    
                    if ("file".equals(type) && path.endsWith(".json")) {
                        String displayName = getFileNameFromPath(path);
                        files.add(new GitHubMappingFile(path, displayName, false));
                        LOG.info("Добавлен JSON-файл: " + path + " -> " + displayName);
                    } else if ("dir".equals(type)) {
                        List<GitHubMappingFile> subFiles = getDirectoryContents(path);
                        files.addAll(subFiles);
                        LOG.info("Из папки " + path + " добавлено файлов: " + subFiles.size());
                    }
                }
                LOG.info("Найдено файлов сопоставлений: " + files.size());
            } else {
                LOG.warn("Пустой ответ GitHub API");
            }
        } catch (IOException | JsonSyntaxException e) {
            LOG.warn("Не удалось получить содержимое репозитория GitHub", e);
        }
        
        return files;
    }
    
    /**
     * Возвращает содержимое папки
     */
    @NotNull
    private List<GitHubMappingFile> getDirectoryContents(String dirPath) {
        List<GitHubMappingFile> files = new ArrayList<>();
        
        try {
            String apiUrl = String.format("%s/repos/%s/%s/contents/%s", 
                GITHUB_API_BASE, REPO_OWNER, REPO_NAME, dirPath);
            String response = makeGitHubApiRequest(apiUrl);
            
            if (response != null) {
                JsonArray contents = gson.fromJson(response, JsonArray.class);
                for (JsonElement element : contents) {
                    JsonObject item = element.getAsJsonObject();
                    String path = item.get("path").getAsString();
                    String type = item.get("type").getAsString();
                    
                    if ("file".equals(type) && path.endsWith(".json")) {
                        String displayName = getFileNameFromPath(path);
                        files.add(new GitHubMappingFile(path, displayName, false));
                    }
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            LOG.warn("Не удалось получить содержимое папки на GitHub: " + dirPath, e);
        }
        
        return files;
    }
    
    /**
     * Скачивает файл библиотеки сопоставлений, перебирая зеркала
     */
    @Nullable
    public String downloadMappingFile(String filePath) {
        try {
            return downloadWithMultipleMirrors(filePath);
        } catch (IOException e) {
            LOG.warn("Не удалось скачать файл библиотеки сопоставлений с GitHub: " + filePath, e);
            return null;
        }
    }
    
    /**
     * Скачивает файл, обращаясь к зеркалам по очереди
     */
    @Nullable
    private String downloadWithMultipleMirrors(String filePath) throws IOException {
        LOG.info("Скачивание файла через зеркала по очереди: " + filePath);
        
        // Пробуем зеркала по очереди
        for (String mirrorUrl : MIRROR_URLS) {
            try {
                String fullUrl = buildMirrorUrl(mirrorUrl, filePath);
                LOG.info("Пробуем зеркало: " + mirrorUrl + ", URL: " + fullUrl);
                
                String content = makeRawContentRequest(fullUrl);
                if (content != null) {
                    LOG.info("Зеркало " + mirrorUrl + " отдало файл: " + filePath);
                    return content;
                }
                
            } catch (Exception e) {
                LOG.warn("Зеркало " + mirrorUrl + " не ответило: " + e.getMessage());
                // Переходим к следующему зеркалу
            }
        }
        
        LOG.warn("Ни одно зеркало не отдало файл: " + filePath);
        return null;
    }
    
    /**
     * Разбирает содержимое файла библиотеки сопоставлений
     */
    @Nullable
    public GitHubMappingLibrary parseMappingFile(String jsonContent) {
        try {
            return gson.fromJson(jsonContent, GitHubMappingLibrary.class);
        } catch (Exception e) {
            LOG.warn("Не удалось разобрать файл библиотеки сопоставлений с GitHub", e);
            return null;
        }
    }
    
    /**
     * Отправляет запрос к GitHub API
     */
    @Nullable
    private String makeGitHubApiRequest(String urlString) throws IOException {
        LOG.info("Запрос к GitHub API: " + urlString);
        
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = createProxyConnection(url);
        
        try {
            // Основные параметры запроса
            connection.setRequestMethod("GET");
            
            // Обычный браузерный User-Agent
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            // GitHub API должен вернуть JSON
            connection.setRequestProperty("Accept", "application/json");
            
            // Тайм-ауты
            connection.setConnectTimeout(5000);  // 5 с на подключение
            connection.setReadTimeout(15000);     // 15 с на чтение
            
            // Кэш отключён, чтобы получить свежие данные
            connection.setUseCaches(false);
            
            LOG.info("Подключение к GitHub API...");
            
            int responseCode = connection.getResponseCode();
            LOG.info("Код ответа GitHub API: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    LOG.info("Запрос к GitHub API выполнен, длина ответа: " + response.length());
                    return response.toString();
                }
            } else if (responseCode == 403) {
                // Ограничение частоты запросов GitHub API
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    String errorMsg = errorResponse.toString();
                    if (errorMsg.contains("rate limit exceeded")) {
                        LOG.warn("Превышен лимит запросов GitHub API: " + errorMsg);
                        throw new IOException("Превышен лимит запросов GitHub API: повторите позже или авторизуйтесь токеном GitHub");
                    } else {
                        LOG.warn("GitHub API отказал в доступе: " + errorMsg);
                        throw new IOException("GitHub API отказал в доступе: " + responseCode);
                    }
                }
            } else {
                LOG.warn("Запрос к GitHub API завершён ошибкой, код ответа: " + responseCode + ", URL: " + urlString);
                throw new IOException("Запрос к GitHub API завершён ошибкой, код ответа: " + responseCode);
            }
            
        } catch (java.net.SocketTimeoutException e) {
            LOG.warn("Тайм-аут подключения к GitHub API: " + e.getMessage());
            throw e;
        } catch (IOException e) {
            LOG.warn("Сетевая ошибка GitHub API: " + e.getMessage());
            throw e;
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * Отправляет запрос за сырым содержимым файла (Raw Content)
     */
    @Nullable
    private String makeRawContentRequest(String urlString) throws IOException {
        LOG.info("Запрос Raw Content: " + urlString);
        
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = createProxyConnection(url);
        
        try {
            // Основные параметры запроса
            connection.setRequestMethod("GET");
            
            // Обычный браузерный User-Agent
            connection.setRequestProperty("User-Agent", 
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            // Только необходимые заголовки
            connection.setRequestProperty("Accept", "*/*");
            
            // Тайм-ауты с запасом на сетевые задержки
            connection.setConnectTimeout(5000);   // 5 с на подключение
            connection.setReadTimeout(15000);     // 15 с на чтение
            
            // Кэш отключён, чтобы получить свежие данные
            connection.setUseCaches(false);
            
            LOG.info("Подключение к Raw Content...");
            
            int responseCode = connection.getResponseCode();
            LOG.info("Код ответа Raw Content: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    LOG.info("Запрос Raw Content выполнен, длина ответа: " + response.length());
                    return response.toString();
                }
            } else {
                LOG.warn("Запрос Raw Content завершён ошибкой, код ответа: " + responseCode + ", URL: " + urlString);
            }
            
        } catch (java.net.SocketTimeoutException e) {
            LOG.warn("Тайм-аут подключения к Raw Content: " + e.getMessage());
            throw e;
        } catch (IOException e) {
            LOG.warn("Сетевая ошибка Raw Content: " + e.getMessage());
            throw e;
        } finally {
            connection.disconnect();
        }
        
        return null;
    }
    
    /**
     * Собирает URL зеркала
     */
    private String buildMirrorUrl(String mirrorUrl, String filePath) {
        if (mirrorUrl.contains("cdn.statically.io")) {
            // cdn.statically.io требует префикс /gh/
            return String.format("%s/gh/%s/%s/%s/%s", 
                mirrorUrl, REPO_OWNER, REPO_NAME, BRANCH, filePath);
        } else {
            // raw.githubusercontent.com и raw.githack.com — стандартный формат
            return String.format("%s/%s/%s/%s/%s", 
                mirrorUrl, REPO_OWNER, REPO_NAME, BRANCH, filePath);
        }
    }
    
    /**
     * Извлекает имя файла из полного пути
     */
    private String getFileNameFromPath(String path) {
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            return path;
        }
        return path.substring(lastSlashIndex + 1);
    }
    
    /**
     * Сведения о файле библиотеки сопоставлений на GitHub
     */
    public static class GitHubMappingFile {
        public final String path;
        public final String displayName;
        public final boolean isDirectory;
        
        public GitHubMappingFile(String path, String displayName, boolean isDirectory) {
            this.path = path;
            this.displayName = displayName;
            this.isDirectory = isDirectory;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Структура данных библиотеки сопоставлений на GitHub
     */
    public static class GitHubMappingLibrary {
        public String name;
        public String version;
        public String description;
        public String author;
        public String[] tags;
        public String lastUpdated;
        public Mappings mappings;
        
        public static class Mappings {
            public java.util.Map<String, String> packages;
            public java.util.Map<String, String> files;
            public java.util.Map<String, String> packageMatch;
            public java.util.Map<String, String> fileMatch;
        }
    }
}
