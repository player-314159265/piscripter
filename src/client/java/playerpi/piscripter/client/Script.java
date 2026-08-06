package playerpi.piscripter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Scanner;

import static playerpi.piscripter.PiScripter.LOGGER;

public class Script {

    String fileName; // file and folder's name

    final String MAIN_PATH = ".\\scripts\\";

    final Path MAIN_FOLDER = Paths.get(MAIN_PATH);

    public Script(String file_name) {
        this.fileName = file_name;
    }

    public String getPath() { return MAIN_PATH + this.fileName; }

    public Path getInfoFile() { return Paths.get(this.getPath() + "\\info.json");}

    public Path getScriptFile() { return Paths.get(this.getPath() + "\\" + this.fileName + ".txt");}

    public Path getScriptFolder() { return Paths.get(this.getPath()); }

    public String getScript(boolean numerate) {
        return readScriptFile(1, -1, numerate);
    }
    public String getScript() {
        return readScriptFile(1, -1, false);
    }

    public String getLine(int lineIndex, boolean numerate) { return readScriptFile(lineIndex, lineIndex, numerate); }
    public String getLine(int lineIndex) { return readScriptFile(lineIndex, lineIndex, false); }

    public int getLength() { // lines.
        int length = 0;
        Path path = this.getScriptFile();
        try (Scanner fileReader = new Scanner(path)) {
            while (fileReader.hasNextLine()) {
                fileReader.nextLine();
                length++;
            }
            return length;
        } catch (IOException e) {
            LOGGER.error("Could not find file {}", this.fileName);
            return 0;
        }
    }

    public boolean exists() {
        return (Files.exists(this.getScriptFolder()));
    }

    public void setLine(int lineIndex, String expression) {
        while (this.getLength() < lineIndex) {
            this.addLine(""); //TODO make it be the correct tabulation level
        }
        String scriptUpToLine = readScriptFile(1, lineIndex-1, false);
        String scriptPastLine = readScriptFile(lineIndex + 1, -1, false);
        try {
            if (scriptPastLine == "") {
                if (scriptUpToLine == "") {
                    Files.writeString(this.getScriptFile(), expression, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(this.getScriptFile(), scriptUpToLine + "\n" + expression, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            } else {
                if (scriptUpToLine == "") {
                    Files.writeString(this.getScriptFile(), expression + "\n" + scriptPastLine, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(this.getScriptFile(), scriptUpToLine + "\n" + expression + "\n" + scriptPastLine, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not write to file: ", e);
        }
    }

    public void insertLine(int lineIndex, String expression) {
        while (this.getLength() < lineIndex) {
            this.addLine(""); //TODO make it be the correct tabulation level
        }
        String scriptUpToLine = readScriptFile(1, lineIndex-1, false);
        String scriptPastLine = readScriptFile(lineIndex, -1, false);
        try {
            if (scriptPastLine == "") {
                if (scriptUpToLine == "") {
                    Files.writeString(this.getScriptFile(), expression, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(this.getScriptFile(), scriptUpToLine + "\n" + expression, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            } else {
                if (scriptUpToLine == "") {
                    Files.writeString(this.getScriptFile(), expression + "\n" + scriptPastLine, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(this.getScriptFile(), scriptUpToLine + "\n" + expression + "\n" + scriptPastLine, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not write to file: ", e);
        }
    }

    public void addLine(String expression) {
        try {
            if (this.getLength() == 0) {
                Files.writeString(this.getScriptFile(), expression, StandardOpenOption.APPEND);
            } else {
                Files.writeString(this.getScriptFile(), "\n" + expression, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            LOGGER.error("Could not write to file: ", e);
        }
    }

    public void removeLines(int lineIndexMin, int lineIndexMax) {
        if ( (this.getLength() < lineIndexMin) || lineIndexMax < lineIndexMin ) { return; }
        if ( this.getLength() < lineIndexMax ) { lineIndexMax = this.getLength(); }

        String scriptUpToLine = readScriptFile(1, lineIndexMin-1, false);
        String scriptPastLine = readScriptFile(lineIndexMax + 1, -1, false);
        try {
            if (scriptPastLine == "") {
                Files.writeString(this.getScriptFile(), scriptUpToLine, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.writeString(this.getScriptFile(), scriptUpToLine + "\n" + scriptPastLine, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Could not remove line(s): ", e);
        }
    }

    public void createScriptFile() {
        if (Files.exists(this.getScriptFolder())) { return; }
        Path newScriptFolder = this.getScriptFolder();
        try {
            Files.createDirectory(newScriptFolder);
            Path scriptFile = this.getScriptFile();
            Path infoFile = this.getInfoFile();

            try {
                Files.createFile(scriptFile);
            } catch (IOException e) {
                LOGGER.error("New script file could not be created within script folder: ", e);
                return;
            }

            try {
                Files.createFile(infoFile);
                try {
                    Files.writeString(infoFile, "{\n" +
                            "\t\"name\" : \"" + this.fileName + "\",\n" +
                            "\t\"language_version\" : 1.0,\n" +
                            "\t\"game_version\" : 26.2,\n" +
                            "\t\"description\" : \"New script\",\n" +
                            "\t\"date_of_creation\" : \"" + LocalDate.now() + "\",\n" +
                            "\t\"authors\" : [\n" +
                            "\t\t\"" + Minecraft.getInstance().getUser().getName() + "\"\n" +
                            "\t],\n" +
                            "\t\n" +
                            "\t\"breakpoints\" : [\n" +
                            "\n" +
                            "\t]\n" +
                            "}");
                    LOGGER.info("New info file written");
                } catch (IOException e) {
                    LOGGER.error("Could not write in the info file: ", e);
                }

            } catch (IOException e) {
                LOGGER.error("New script info file could not be created within folder: ", e);
                return;
            }

            LOGGER.info("Script files were constructed successfully!");

        } catch (IOException e) {
            LOGGER.error("New script folder could not be created: ", e);
        }
    }

    public String readScriptFile(int lineMin, int lineMax, boolean numerate) {

        String[] lines = readScriptFileLines(lineMin, lineMax);

        if (lines == null) { return ""; }

        String fileCode = "";
        int index = lineMin;
        for (String line : lines) {
            if (numerate) {
                fileCode += index++ + ". " + line + "\n";
            } else {
                fileCode += line + "\n";
            }
        }
        if ( fileCode.length() > 1 ) {
            return fileCode.substring(0, fileCode.length()-1); // remove the last line feed
        } else {
            LOGGER.warn("Script file {} either empty or not defined", this.fileName);
            return "";
        }
    } // -1 = until the end of the file

    public String[] readScriptFileLines(int lineMin, int lineMax) {
        if (lineMax < lineMin && lineMax != -1) { return null; }
        Path path = this.getScriptFile();
        try {
            Object[] lines = Files.readAllLines(path).toArray();
            if (lineMax == -1 || lineMax > lines.length) { lineMax = lines.length; }
            String[] newLines = new String[lineMax - (lineMin-1)];
            int index = 0;
            for (Object line : lines) {
                if (index == lineMax) { break; }
                if (index >= lineMin -1) { newLines[index - lineMin +1] = line.toString(); }
                index++;
            }
            return newLines;
        } catch (IOException e) {
            LOGGER.error("Could not read file: ", e);
            return null;
        }
    } // -1 = until the end of the file

    public void delete() {
        Path folder = this.getScriptFolder();
        Path script = this.getScriptFile();
        Path info = this.getInfoFile();
        try {
            Files.delete(script);
            Files.delete(info);
            Files.delete(folder);
        } catch (IOException e) {
            LOGGER.error("Could not delete file: ", e);
        }
    }

    public void openFolder() {
        Path folderToOpen = this.getScriptFolder();

        if (Files.exists(folderToOpen)) {
            Util.getPlatform().openFile(folderToOpen.toFile());
        }
    }

    public String getInfoDescription() { return PiScripterClient.JsonHandler.loadConfig(this.getInfoFile()).description; }
    public String getInfoGameVersion() { return PiScripterClient.JsonHandler.loadConfig(this.getInfoFile()).game_version; }
    public String getInfoLanguageVersion() { return PiScripterClient.JsonHandler.loadConfig(this.getInfoFile()).language_version; }
    public String getInfoName() { return PiScripterClient.JsonHandler.loadConfig(this.getInfoFile()).name; }
    public String getInfoDateOfCreation() { return PiScripterClient.JsonHandler.loadConfig(this.getInfoFile()).date_of_creation; }
    public String[] getInfoAuthors() { return PiScripterClient.JsonHandler.loadConfig(this.getInfoFile()).authors; }
    public String getInfoAuthorsString() {
        String[] authors = this.getInfoAuthors();
        String formattedAuthors = "";
        for (String author : authors) {
            formattedAuthors += author + ", ";
        }
        if (formattedAuthors.length() > 1) {
            return formattedAuthors.substring(0, formattedAuthors.length()-2);
        } else {
            return "";
        }
    }
    public int[] getInfoBreakpoints() { return PiScripterClient.JsonHandler.loadConfig(this.getInfoFile()).breakpoints; }
    public String getInfoBreakpointsString() {
        int[] breakpoints = this.getInfoBreakpoints();
        String formattedBreakpoints = "";
        for (int breakpoint : breakpoints) {
            formattedBreakpoints += breakpoint + ", ";
        }
        if (formattedBreakpoints.length() > 1) {
            return formattedBreakpoints.substring(0, formattedBreakpoints.length()-2);
        } else {
            return "";
        }
    }

    public void setInfoFileValue(String id, Object newValue) { PiScripterClient.JsonHandler.setGsonConfig(this.getInfoFile(), id, newValue);}

}
