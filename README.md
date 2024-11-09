<!-- Plugin description -->
# FunctionTextObj (IdeaVim extension)

An extension for IdeaVim that adds text objects for manipulating
functions/methods in your code. Similar to how `iw` operates on words or `i"`
operates on quoted strings, this plugin provides `if` and `af` to operate on
functions.

## Features

* `if` - "inner function" - selects the function body (excluding braces)
* `af` - "around function" - selects the entire function (including braces and signature)

These text objects work with all Vim operators and in visual mode:

```
dif  - delete the function body
caf  - change the entire function
vif  - visually select the function body
yaf  - yank (copy) the entire function
```

## Installation

1. Install [IdeaVim](https://plugins.jetbrains.com/plugin/164-ideavim) if you
   haven't already
2. Install this plugin:
    - In IntelliJ IDEA: Settings/Preferences → Plugins → Marketplace → Search
      for "Vim FunctionTextObj"
    - Or download from
      [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/YOUR_PLUGIN_ID)
3. Add `set functiontextobj` to your `~/.ideavimrc` file, then run
    `:source ~/.ideavimrc` or restart the IDE.

## Customization

Don't like the default f mapping? You can remap the text objects in your
`.ideavimrc`:

```
" Use 'm' instead
map im <Plug>InnerFunction
map am <Plug>OuterFunction
```
<!-- Plugin description end -->
