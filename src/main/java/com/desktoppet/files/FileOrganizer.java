package com.desktoppet.files;

import com.desktoppet.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class FileOrganizer {
    private static final int MAX_TEXT_CHARS = 9000;

    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, PreviewResult> previews = new ConcurrentHashMap<>();
    private final OpenAiChatModel model;

    public FileOrganizer(AppConfig config) {
        this.config = config;
        if (config.deepSeekApiKey().isBlank() || "replace-me".equals(config.deepSeekApiKey())) {
            this.model = null;
        } else {
            this.model = OpenAiChatModel.builder()
                    .baseUrl(config.deepSeekBaseUrl())
                    .apiKey(config.deepSeekApiKey())
                    .modelName(config.deepSeekChatModel())
                    .timeout(Duration.ofSeconds(60))
                    .build();
        }
    }

    public PreviewResult preview(PreviewRequest request) {
        requireModel();
        Path sourceRoot = normalizeUserPath(request.sourceRoot()).toAbsolutePath().normalize();
        if (!isAllowed(sourceRoot)) {
            throw new IllegalArgumentException("拒绝访问：该路径不在白名单目录中。请在整理弹窗中覆盖白名单，或在聊天里说“把D盘加入白名单”。");
        }
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("源目录不存在或不是目录：" + sourceRoot);
        }
        List<String> extensions = parseExtensions(request.extensions());
        if (extensions.isEmpty()) {
            extensions = List.of("pdf");
        }
        String instruction = request.instruction() == null || request.instruction().isBlank()
                ? "按主题分类文献"
                : request.instruction().trim();

        List<Candidate> matched = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                if (!matchesExtension(file, extensions)) {
                    continue;
                }
                scanned++;
                try {
                    Candidate candidate = classify(file, instruction);
                    if (candidate.included()) {
                        matched.add(candidate);
                    }
                } catch (Exception e) {
                    failures.add(file + "：读取或分类失败：" + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("扫描目录失败：" + e.getMessage(), e);
        }
        String previewId = UUID.randomUUID().toString();
        PreviewResult result = new PreviewResult(previewId, sourceRoot.toString(), instruction, scanned, matched, failures);
        previews.put(previewId, result);
        return result;
    }

    public ConfirmResult confirm(String previewId) {
        PreviewResult preview = previews.get(previewId);
        if (preview == null) {
            throw new IllegalArgumentException("预览不存在或已过期：" + previewId);
        }
        Path targetRoot = config.exportDir()
                .resolve("job-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
                .toAbsolutePath()
                .normalize();
        List<CopiedFile> copied = new ArrayList<>();
        List<String> failures = new ArrayList<>(preview.failures());
        try {
            Files.createDirectories(targetRoot);
            for (Candidate candidate : preview.candidates()) {
                try {
                    Path source = Path.of(candidate.sourcePath());
                    String categoryName = safeDirectoryName(candidate.category());
                    Path category = targetRoot.resolve(categoryName);
                    Files.createDirectories(category);
                    Path destination = uniqueDestination(category, source.getFileName().toString());
                    Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    copied.add(new CopiedFile(
                            candidate.fileName(),
                            candidate.sourcePath(),
                            destination.toString(),
                            categoryName,
                            candidate.summary(),
                            candidate.reason()
                    ));
                } catch (Exception e) {
                    failures.add(candidate.sourcePath() + "：复制失败：" + e.getMessage());
                }
            }
            Path report = targetRoot.resolve("目录.xlsx");
            writeReport(report, copied);
            previews.remove(previewId);
            return new ConfirmResult(previewId, copied.size(), targetRoot.toString(), report.toString(), copied, failures);
        } catch (IOException e) {
            throw new IllegalArgumentException("写入整理结果失败：" + e.getMessage(), e);
        }
    }

    public ParsedRequest parseNaturalRequest(String message) {
        String sourceRoot = parseSourceRoot(message);
        String extensions = parseExtensionText(message);
        return new ParsedRequest(sourceRoot, extensions, message == null ? "" : message.trim());
    }

    public List<String> allowedRoots() {
        return config.allowedRoots().stream()
                .map(Path::toString)
                .toList();
    }

    public AllowedRootsResult replaceAllowedRoots(List<String> roots) {
        List<Path> normalized = roots.stream()
                .map(this::normalizeUserPath)
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .toList();
        config.replaceAllowedRoots(normalized);
        persistAllowedRoots();
        return new AllowedRootsResult(allowedRoots());
    }

    public AllowedRootsResult replaceAllowedRootsFromMessage(String message) {
        String sourceRoot = parseSourceRoot(message);
        if (sourceRoot == null || sourceRoot.isBlank()) {
            throw new IllegalArgumentException("没有识别到要设置的白名单路径。请说“把D盘加入白名单”或“设置文件白名单为D:\\papers”。");
        }
        return replaceAllowedRoots(List.of(sourceRoot));
    }

    public String summarizeAllowedRoots(AllowedRootsResult result) {
        if (result.roots().isEmpty()) {
            return "文件整理白名单已清空。";
        }
        return "文件整理白名单已更新为：" + String.join("；", result.roots());
    }

    public String summarizePreview(PreviewResult preview) {
        StringBuilder builder = new StringBuilder();
        builder.append("已生成整理预览，编号：").append(preview.previewId()).append("\n");
        builder.append("扫描目录：").append(preview.sourceRoot()).append("\n");
        builder.append("扫描匹配扩展名文件：").append(preview.scannedCount()).append(" 个，命中文献：").append(preview.candidates().size()).append(" 个。\n");
        preview.candidates().stream().limit(8).forEach(candidate ->
                builder.append("- [").append(candidate.category()).append("] ")
                        .append(candidate.fileName()).append("：")
                        .append(candidate.summary()).append("\n")
        );
        if (preview.candidates().size() > 8) {
            builder.append("还有 ").append(preview.candidates().size() - 8).append(" 个候选未显示。\n");
        }
        builder.append("确认后我会复制归档并生成目录.xlsx。请输入“确认整理”执行。");
        return builder.toString();
    }

    public String summarizeConfirm(ConfirmResult result) {
        return "整理完成：复制 " + result.copiedCount()
                + " 个文件，输出目录：" + result.outputRoot()
                + "，目录表：" + result.reportPath();
    }

    private Candidate classify(Path file, String instruction) throws IOException {
        String text = extractText(file);
        String prompt = """
                你是严谨的文献整理助手。请根据用户要求判断文件是否应该进入整理结果，并给出分类。
                用户要求：
                %s

                文件名：
                %s

                文件内容摘录：
                %s

                必须只输出 JSON，不要输出解释。字段：
                {
                  "included": true/false,
                  "isLiterature": true/false,
                  "category": "简短中文分类名",
                  "summary": "一句话中文摘要",
                  "reason": "一句话说明判断理由"
                }
                只有同时满足主题相关和像论文/文献/技术报告时，included 才为 true。
                """.formatted(instruction, file.getFileName(), text);
        JsonNode json = parseJson(model.chat(prompt));
        boolean included = json.path("included").asBoolean(false);
        boolean isLiterature = json.path("isLiterature").asBoolean(false);
        String category = textOrDefault(json.path("category").asText(), "未分类");
        String summary = textOrDefault(json.path("summary").asText(), "无摘要");
        String reason = textOrDefault(json.path("reason").asText(), "无判断理由");
        return new Candidate(
                file.getFileName().toString(),
                file.toAbsolutePath().normalize().toString(),
                included,
                isLiterature,
                safeDirectoryName(category),
                summary,
                reason
        );
    }

    private String extractText(Path file) throws IOException {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            try (PDDocument document = Loader.loadPDF(file.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(6, document.getNumberOfPages()));
                return truncate(stripper.getText(document));
            }
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return truncate(Files.readString(file));
        }
        return "文件格式暂不支持全文抽取，仅使用文件名判断。";
    }

    private JsonNode parseJson(String raw) throws IOException {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return mapper.readTree(text);
    }

    private List<String> parseExtensions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,，;；\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith(".") ? s.toLowerCase(Locale.ROOT) : "." + s.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private boolean matchesExtension(Path file, List<String> extensions) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(name::endsWith);
    }

    private Path normalizeUserPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("sourceRoot 不能为空");
        }
        String value = raw.trim().replace('\\', '/');
        if (value.matches("^[A-Za-z]:/?$")) {
            return Path.of("/mnt/" + Character.toLowerCase(value.charAt(0)));
        }
        if (value.matches("^[A-Za-z]:/.+")) {
            return Path.of("/mnt/" + Character.toLowerCase(value.charAt(0)) + value.substring(2));
        }
        return Path.of(value);
    }

    private void persistAllowedRoots() {
        Path configPath = Path.of("config", "application.properties");
        String line = "pet.allowedRoots=" + config.allowedRootsPropertyValue();
        try {
            Files.createDirectories(configPath.getParent());
            List<String> lines = Files.isRegularFile(configPath)
                    ? Files.readAllLines(configPath)
                    : new ArrayList<>();
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("pet.allowedRoots=")) {
                    lines.set(i, line);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add(line);
            }
            Files.write(configPath, lines);
        } catch (IOException e) {
            throw new IllegalStateException("写入白名单配置失败：" + e.getMessage(), e);
        }
    }

    private String parseSourceRoot(String message) {
        if (message == null) {
            return "";
        }
        String normalized = message.replace('\\', '/');
        java.util.regex.Matcher pathMatcher = java.util.regex.Pattern.compile("([A-Za-z]:/[^\\s，,。；;]+|/mnt/[A-Za-z]/[^\\s，,。；;]*|/mnt/[A-Za-z])").matcher(normalized);
        if (pathMatcher.find()) {
            return normalizeUserPath(pathMatcher.group(1)).toString();
        }
        java.util.regex.Matcher driveMatcher = java.util.regex.Pattern.compile("([A-Za-z])\\s*盘").matcher(message);
        if (driveMatcher.find()) {
            return "/mnt/" + driveMatcher.group(1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private String parseExtensionText(String message) {
        if (message == null) {
            return "pdf";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("pdf")) {
            return "pdf";
        }
        if (lower.contains("docx")) {
            return "docx";
        }
        if (lower.contains("md")) {
            return "md";
        }
        if (lower.contains("txt")) {
            return "txt";
        }
        return "pdf";
    }

    private boolean isAllowed(Path path) {
        return config.allowedRoots().stream()
                .map(root -> root.toAbsolutePath().normalize())
                .anyMatch(path::startsWith);
    }

    private void requireModel() {
        if (model == null) {
            throw new IllegalStateException("模型未配置，无法进行 AI 内容分类。请配置 DEEPSEEK_API_KEY、DEEPSEEK_BASE_URL 和 DEEPSEEK_CHAT_MODEL。");
        }
    }

    private Path uniqueDestination(Path category, String fileName) {
        Path destination = category.resolve(fileName);
        if (!Files.exists(destination)) {
            return destination;
        }
        String base = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }
        int index = 2;
        while (Files.exists(category.resolve(base + "-" + index + extension))) {
            index++;
        }
        return category.resolve(base + "-" + index + extension);
    }

    private String safeDirectoryName(String value) {
        String cleaned = textOrDefault(value, "未分类")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
        return cleaned.isBlank() ? "未分类" : cleaned;
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_TEXT_CHARS ? text : text.substring(0, MAX_TEXT_CHARS);
    }

    private void writeReport(Path report, List<CopiedFile> files) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("目录");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("序号");
            header.createCell(1).setCellValue("文件名");
            header.createCell(2).setCellValue("原路径");
            header.createCell(3).setCellValue("新路径");
            header.createCell(4).setCellValue("分类");
            header.createCell(5).setCellValue("摘要");
            header.createCell(6).setCellValue("判断理由");
            for (int i = 0; i < files.size(); i++) {
                CopiedFile file = files.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(file.fileName());
                row.createCell(2).setCellValue(file.sourcePath());
                row.createCell(3).setCellValue(file.destinationPath());
                row.createCell(4).setCellValue(file.category());
                row.createCell(5).setCellValue(file.summary());
                row.createCell(6).setCellValue(file.reason());
            }
            for (int i = 0; i <= 6; i++) {
                sheet.autoSizeColumn(i);
            }
            try (OutputStream out = Files.newOutputStream(report)) {
                workbook.write(out);
            }
        }
    }

    public record PreviewRequest(String sourceRoot, String extensions, String instruction) {
    }

    public record PreviewResult(
            String previewId,
            String sourceRoot,
            String instruction,
            int scannedCount,
            List<Candidate> candidates,
            List<String> failures
    ) {
    }

    public record Candidate(
            String fileName,
            String sourcePath,
            boolean included,
            boolean isLiterature,
            String category,
            String summary,
            String reason
    ) {
    }

    public record ConfirmResult(
            String previewId,
            int copiedCount,
            String outputRoot,
            String reportPath,
            List<CopiedFile> copiedFiles,
            List<String> failures
    ) {
    }

    public record CopiedFile(
            String fileName,
            String sourcePath,
            String destinationPath,
            String category,
            String summary,
            String reason
    ) {
    }

    public record ParsedRequest(String sourceRoot, String extensions, String instruction) {
    }

    public record AllowedRootsResult(List<String> roots) {
    }
}
