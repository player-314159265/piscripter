# piScripter
*by player_314159265, version 1.0.0*

## Files
Find all of your script files at .minecraft\scripts\
That folder is a folder created by the mod if it doesn't exist already when launching the game

Every script is composed of a `.txt` containing the code, which you can freely edit without consequence (as long as the code works! heh) and an `info.json` file which you can edit, but I wouldn't recommend touching it
if you're not sure about what you're doing.
**Do not change the name of the folder or script only to change the name of the script!**
Instead, use `/piscript scripts <old name> rename <new name>` 

## How to Use the Command /piscript
This is an all in one command for the mod, letting you modify and execute scripts or bits of code, or get more info on the scripts themselves. It is a command version of the menu

`/piscript` - opens the menu;
`/piscript console` - opens the console in the menu;
`/piscript console from <script> <expression>` - runs the expression `<expression>`, being able to use the functions and variables from `<script>`;
`/piscript console raw <expression>` - runs the expression `<expression>` directly;
`/piscript scripts` - opens the scripts menu;
`/piscript scripts <script>` - opens the script `<script>`'s menu;
`/piscript scripts <script> info` - shows info about the script `<script>`;
`/piscript get` - shows the entire script `<script>`;
