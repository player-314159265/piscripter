package playerpi.piscripter.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecuteScripts extends Thread {

    Map<Script, String> scriptsRunning = new HashMap<>(); // script: normal/debug
    Map<Script, Integer> currentLine = new HashMap<>(); // script: int      first line of currentLine = 0, not 1 like the line indexes.
    Map<Script, Map<String, Object>> scriptVariables = new HashMap<>(); // script: maps

    public void run() {

        while (true) {

        }

    }

    public void executeScript() {

    }

    public void startScript(Script script, String model) {
        if (model != "normal" && model != "debug") { return; }
        scriptsRunning.put(script, model);
        currentLine.put(script, 0);
    }

    public void stopScript(Script script) {
        scriptsRunning.remove(script);
        currentLine.remove(script);
    }

    public boolean isScriptActive(Script script) {
        if (scriptsRunning.containsKey(script)) {
            return (scriptsRunning.get(script) == "normal" || scriptsRunning.get(script) == "normal");
        } else {
            return false;
        }
    }

    public int getCurrentLine(Script script) {
        if (currentLine.containsKey(script)) {
            return currentLine.get(script);
        }
        return 0;
    }

    public void nextLine(Script script) {
        currentLine.put(script, this.getCurrentLine(script) + 1);
    }

    public String getLineFromIndex(Script script, int index) {
        return script.getLine(index + 1);
    }

    public String getLine(Script script) {
        return script.getLine(currentLine.get(script) + 1);
    }

    public Object getVar(Script script, String variable) {
        return scriptVariables.get(script).get(variable);
    }

    public void setVar(Script script, String variable, Object value) {
         scriptVariables.get(script).put(variable, value);
    }

    public int getIndentation(Script script, int lineIndex) {
        String line = getLineFromIndex(script, lineIndex);
        int indentation = 0;
        for (char character : line.toCharArray()) {
            if (line.charAt(0) == '\t') {
                indentation += 4;
            } else if (line.charAt(0) == ' ') {
                indentation++;
            } else {
                break;
            }
        }
        return indentation;
    }

    public Map<String, Object> getStatement(Script script, int lineIndex) {
        String line = getLineFromIndex(script, lineIndex);
        Map<String, Object> statement = new HashMap<>();

        if (line.matches("^[\\t ]*[0-9_]*[a-zA-Z]+\\w* ?= ?[^ ].*$")) { // variable declaration / modifying
            statement.put("type", "set_var");

            Pattern patternVariable = Pattern.compile("[0-9_]*[a-zA-Z]+\\w*");
            Matcher matcherVariable = patternVariable.matcher(line);
            statement.put("variable", matcherVariable.group());

            Pattern patternValue = Pattern.compile("= ?[^ ].*$");
            Matcher matcherValue = patternValue.matcher(line);
            if (matcherValue.group().charAt(1) != ' ') {
                statement.put("value", matcherValue.group().substring(1));
            } else {
                statement.put("value", matcherValue.group().substring(2));
            }
        }

        if (line.matches("^import \\w$")) { // import a script
            statement.put("type", "import");
            statement.put("script", new Script(line.substring(7)));
        }

        if (line.matches("^[\\t ]*if [^ ].*[^ ]:$")) { // if
            statement.put("type", "if");

            Pattern patternCondition = Pattern.compile(".*:$");
            Matcher matcherCondition = patternCondition.matcher(line);
            statement.put("condition", matcherCondition.group().substring(0, matcherCondition.group().length() -1 ));
        }

        if (line.matches("^[\\t ]*while [^ ].*[^ ]:$")) { // while
            statement.put("type", "while");

            Pattern patternCondition = Pattern.compile(".*:$");
            Matcher matcherCondition = patternCondition.matcher(line);
            statement.put("condition", matcherCondition.group().substring(0, matcherCondition.group().length() -1 ));
        }

        if (line.matches("^[\\t ]*for [0-9_]*[a-zA-Z]+\\w* in [^ ].*[^ ]:$")) { // for
            statement.put("type", "if");
            String[] lineCut = line.split(" "); //FIXME
            Pattern patternCondition = Pattern.compile(".*:$");
            Matcher matcherCondition = patternCondition.matcher(line);
            statement.put("condition", matcherCondition.group().substring(0, matcherCondition.group().length() -1 ));
        }

        //(?'argument'(?(DEFINE)(?'function'[0-9_]*[a-zA-Z]+\w*\( *(?P>argument)? *\)))(?(DEFINE)(?'variable'[0-9_]*[a-zA-Z]+\w*))(?(DEFINE)(?'int'\d+))(?(DEFINE)(?'float'\d*\.\d+))(?(DEFINE)(?'string'(?<!\\)"[^"]*(?<=\\)"))(?P>string))
        //"((?:(?=\\)\\"|\\.)|[^\\"])*"

        //(?'argument'(?(DEFINE)(?'function'[0-9_]*[a-zA-Z]+\w*\( *(?P>argument)? *\)))(?(DEFINE)(?'variable'[0-9_]*[a-zA-Z]+\w*))(?(DEFINE)(?'int'\d+))(?(DEFINE)(?'float'\d*\.\d+))(?(DEFINE)(?'string'"((?:(?=\\)\\"|\\.)|[^\\"])*"))(?(DEFINE)(?'returnsnum'(?P>float)|(?P>int)|(?P>subcustom)))(?(DEFINE)(?'returnsstring'(?P>string)|(?P>subcustom)))(?(DEFINE)(?'math'((((?P>returnsnum))|(\( *(?P>returnsnum) *\)))( *(\+|-|\*|\*\*|\/|\/\/|%|\^|\|) *(((?P>returnsnum))|(\( *(?P>returnsnum) *\))))+)))(?(DEFINE)(?'appendstring'(?P>returnsstring)( *\+ *(?P>returnsstring))+))(?(DEFINE)(?'custom'(?P>function)|(?P>variable)))(?(DEFINE)(?'subcustom'(?P>custom)( *\. *(?P>custom))*))(?P>math))
        //(?'argument'(?(DEFINE)(?'function'[0-9_]*[a-zA-Z]+\w*\( *(?P>argument)? *\)))(?(DEFINE)(?'variable'[0-9_]*[a-zA-Z]+\w*))(?(DEFINE)(?'int'\d+))(?(DEFINE)(?'float'\d*\.\d+))(?(DEFINE)(?'string'"((?:(?=\\)\\"|\\.)|[^\\"])*"))(?(DEFINE)(?'returnsnum'(?P>float)|(?P>int)|(?P>subcustom)))(?(DEFINE)(?'returnsstring'(?P>string)|(?P>subcustom)))(?(DEFINE)(?'math'((?P>returnsnum)|\( *(?P>returnsnum) *\)|\( *(?P>math) *\))( *(\+|-|\*|\*\*|\/|\/\/|%|\^|\|) *((?P>returnsnum)|\( *(?P>returnsnum) *\)|\( *(?P>math) *\)))+|\( *((?P>returnsnum)|\( *(?P>returnsnum) *\)|\( *(?P>math) *\))( *(\+|-|\*|\*\*|\/|\/\/|%|\^|\|) *((?P>returnsnum)|\( *(?P>returnsnum) *\)|\( *(?P>math) *\)))+ *\)))(?(DEFINE)(?'appendstring'(?P>returnsstring)( *\+ *(?P>returnsstring))+))(?(DEFINE)(?'custom'(?P>function)|(?P>variable)))(?(DEFINE)(?'subcustom'(?P>custom)( *\. *(?P>custom))*))(?P>math))

        //(?'argument'(?(DEFINE)(?'function'[0-9_]*[a-zA-Z]+\w*\( *(?P>argument)? *\)))(?(DEFINE)(?'variable'[0-9_]*[a-zA-Z]+\w*))(?(DEFINE)(?'int'\d+))(?(DEFINE)(?'float'\d*\.\d+))(?(DEFINE)(?'string'"((?:(?=\\)\\"|\\.)|[^\\"])*"))(?(DEFINE)(?'returnsnum'(?P>float)|(?P>int)|(?P>unknown)))(?(DEFINE)(?'returnsstring'(?P>string)|(?P>unknown)))(?(DEFINE)(?'math'((?P>returnsnum)|\( *(?P>returnsnum) *\)|\( *(?P>math) *\))( *(\+|-|\*|\*\*|\/|\/\/|%|\^|\||&) *((?P>returnsnum)|\( *(?P>returnsnum) *\)|\( *(?P>math) *\)))+))(?(DEFINE)(?'appendstring'(?P>returnsstring)( *\+ *(?P>returnsstring))+))(?(DEFINE)(?'custom'(?P>function)|(?P>variable)))(?(DEFINE)(?'subcustom'(?P>custom)( *\. *(?P>custom))*))(?(DEFINE)(?'array'\[ *((?P>argument) *, *)*(?P>argument)? *\]))(?(DEFINE)(?'list'\( *((?P>argument) *, *)*(?P>argument)? *\)))(?(DEFINE)(?'dictionary'{ *((?P>argument) *: *(?P>argument) *, *)*((?P>argument) *: *(?P>argument))?}))(?(DEFINE)(?'indexediterator'((?P>subcustom)|(?P>dictionarykey)|(?P>array)|(?P>list)|(?P>string))( *\[ *(?P>int) *\])+))(?(DEFINE)(?'dictionarykey'(?P>dictionary) *\[ *(?P>argument) *\]))(?(DEFINE)(?'unknown'(?P>subcustom)|(?P>indexediterator)|(?P>dictionarykey)))(?(DEFINE)(?'condition'(?!\()((?'allbutbool'(?P>unknown)|(?P>math)|(?P>returnsnum)|(?P>returnsstring)|(?P>dictionary)|(?P>list)|(?P>array)|(?P>indexediterator))|\( *(?P>allbutbool) *\)|\( *(?P>condition) *\))( *(==|!=|<=|>=|<|>) *((?P>allbutbool)|\( *(?P>allbutbool) *\)|\( *(?P>condition) *\)))+))(?(DEFINE)(?'logicgate'(?'not'(?<!\w)(not +)+(?'bool'(?P>condition)|(?P>unknown)|(?P>not)|\((?P>bool)\)|\((?P>andor)\)))|(?'andor'(?P>bool)( +(or|and) +(?P>bool))+)))(?P>logicgate))
        return statement;
        //optimising it:
        //
        //(?(DEFINE)(?'argument'(?(DEFINE)(?'suffixes'( *\( *((?P>argument) *(, *(?P>argument) *)*)?\))?( *\[ *(?P>argument) *\])*))(?(DEFINE)(?'unknown'[0-9_]*[a-zA-Z]+\w*(?P>suffixes)?)(?(DEFINE)(?'string'"((?:(?=\\)\\"|\\.)|[^\\"])*"))(?(DEFINE)(?'iterators'(?P>string)(?P>))))(?P>iterators)))(?P>argument)
    }

}
