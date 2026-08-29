# piScripter
*by player_314159265, version 1.0.0*

## Files
Find all of your script files at .minecraft\scripts\
That folder is a folder created by the mod if it doesn't exist already when launching the game

Every script is composed of a `.txt` containing the code, which you can freely edit without consequence (as long as the code works! heh) and an `info.json` file which you can edit, but I wouldn't recommend touching it
if you're not sure about what you're doing.
**Do not change the name of the folder or script only to change the name of the script!**
Instead, use `/piscript scripts <old name> rename <new name>` 

## the Command /piscript
This is an all in one command for the mod, letting you modify and execute scripts or bits of code, or get more info on .the scripts themselves. It is a command version of the menu.
Refer to your scripts with their name, not their path, and not like name.txt or something
Note that your scripts' name must be made of only letters, numbers, and underscores.
Note that script lines start at 1, not 0.

`/piscript` - opens the menu;

### console

`/piscript console` - opens the console in the menu;

`/piscript console from <script> <expression>` - runs the expression `<expression>`, being able to use the functions and variables from `<script>`;

`/piscript console raw <expression>` - runs the expression `<expression>` directly;

### scripts

`/piscript scripts` - opens the scripts menu;

`/piscript scripts <script>` - opens the script `<script>`'s menu;

`/piscript scripts <script> info` - shows info about the script `<script>`;

#### scripts - get

`/piscript scripts <script> get` - shows the entire script `<script>`;

`/piscript scripts <script> get all` - shows the entire script `<script>`;

`/piscript scripts <script> get line <index>` - shows the line `<index>` of the script `<script>`;

`/piscript scripts <script> get lines <minimum>` - shows all the lines after line `<minimum>` (included) of the script `<script>`;

`/piscript scripts <script> get lines <minimum> <maximum>` - shows all the lines after line `<minimum>` (included) up to of the script `<script>`;

#### scripts - edit

`/piscript scripts <script> edit setline <index> <expression>` - sets the code at line `<index>` of script `<script>` to `<expression>`;

`/piscript scripts <script> edit addline <expression>` - appends a line to the end of the script with the code `<expression>`;

`/piscript scripts <script> edit insertline <index> <expression>` - inserts the line `<expression>` at index `<index>`;

`/piscript scripts <script> edit removeline <index>` - removes the line at index `<index>` of script `<script>`;

`/piscript scripts <script> edit removelines <minimum> <maximum>` - removes every line of script `<script>` from `<minimum>` to `<maximum>` (inclusive);

#### scripts - run

`/piscript scripts <script> run` - executes the script `<script>` in normal mode;

`/piscript scripts <script> run normal` - executes the script `<script>` in normal mode;

`/piscript scripts <script> run debug` - executes the script `<script>` in debug mode;

#### scripts - breakpoint

`/piscript scripts <script> breakpoint list` - lists every breakpoint placed in script `<script>`;

`/piscript scripts <script> breakpoint toggleat <index>` - enables (or disables if the breakpoint is already there) a breakpoint at index `<index>` of script `<script>`;

`/piscript scripts <script> breakpoint removeall` - disables every breakpoint of script `<script>`;

#### scripts - *other*

`/piscript scripts <script> delete` - warns about deleting the file. **Does not delete it**;

`/piscript scripts <script> delete <scriptAgain>` - if `<scriptAgain>` equals `<script>`, deletes the script. This is to prevent accidental deletion and serves as confirmation;
 
`/piscript scripts <script> folder` - opens the script's folder in File Explorer;

`/piscript scripts <script> duplicate <name>` - duplicates the script `<script>`. The duplicated script will be named `<name>`;

`/piscript scripts <script> rename <name>` - renames the script `<script>` to `<name>`;

`/piscript scripts <script> stop` - stops the script `<script>`;

### create

`/piscript create <scriptName>` - creates a new script named `<scriptName>`;

### folder

`/piscript folder` - opens the main scripts folder;

`/piscript folder <script>` - opens the script `<script>`;
