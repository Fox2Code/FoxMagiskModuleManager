# Fox's Magisk Module Manager (Developer documentation)

Note: This doc assume you already read the
[official Magisk module developer guide](https://topjohnwu.github.io/Magisk/guides.html)

Note: official repo do not accept new modules anymore, submit
[here](https://github.com/Magisk-Modules-Alt-Repo/submission) instead.

Index:  
- [Properties](DEVELOPERS.md#properties)
- [Installer commands](DEVELOPERS.md#installer-commands)

## Properties

In addition to the following magisk properties
```properties
# Magisk supported properties
id=<string>
name=<string>
version=<string>
versionCode=<int>
author=<string>
description=<string>
```

This the manager support these new properties
```properties
# Fox's Mmm supported properties
minApi=<int>
maxApi=<int>
minMagisk=<int>
support=<url>
donate=<url>
config=<package>
```
(Note: All urls must start with `https://`, or else will be ignored)

- `minApi` and `maxApi` tell the manager which is the SDK version range the module support  
  (See: [Codenames, Tags, and Build Numbers](https://source.android.com/setup/start/build-numbers))
- `minMagisk` tell the manager which is the minimum Magisk version required for the module
  (Often for magisk `xx.y` the version code is `xxy00`)
- `support` support link to direct users when they need support for you modules
- `donate` donate link to direct users to where they can financially support your project
- `config` package name of the application that configure your module
  (Note: The icon won't appear in the module list if the module and target app is not installed)

Note: Fox's Mmm use fallback 
[here](app/src/main/java/com/fox2code/mmm/utils/PropUtils.java#L21)
for some modules  
Theses values are only used if not defined in the `module.prop` files  
So the original module maker can still override them

## Installer commands

The Fox's Mmm also allow better control over it's installer interface

Fox's Mmm defined the variable `MMM_EXT_SUPPORT` to expose it's extension support

All the commands start with it `#!`, by default the manager process command as log output
unless `#!useExt` is sent to indicate that the app is ready to use commands

Commands:
- `useExt`: Tell the manager you would like to use commands  
  (Note: Any command executed before this one will just appear as log in the console)
- `addLine <arg>`: Add line to the terminal, this commands can be useful if 
  you want to display text that start with `#!` inside the terminal
- `setLastLine <arg>`: Set the last line of text displayed in the terminal  
  (Note: If the terminal is empty it will just add a new line)
- `clearTerminal`: Clear the terminal of any text, making it empty
- `scrollUp`: Scroll up at the top of the terminal
- `scrollDown`: Scroll down at the bottom of the terminal
- `showLoading`: Show an indeterminate progress bar
  (Note: the bar is automatically hidden when the install finish)
- `hideLoading`: Hide the indeterminate progress bar if previously shown
- `setSupportLink <url>`: Set support link to show when the install finish  
  (Note: Modules installed from repo will not show the config button if a link is set)

Note: 
The current behavior with unknown command is to ignore them, 
I may add or remove commands in the future depending of how they are used

A wrapper script to use theses commands could be
```sh
if [ -n "$MMM_EXT_SUPPORT" ]; then
  ui_print "#!useExt"
  mmm_exec() { 
    ui_print "$(echo "#!$@")"
  }
else
  mmm_exec() { true; }
fi
```
And there is an instance of it in use
```sh
# mmm_exec only take effect if inside the loader
mmm_exec showLoading
ui_print "The installer doesn't support mmm_exec"
mmm_exec setLastLine "The installer support mmm_exec"
# Wait to simulate the module doing something
sleep 5
mmm_exec hideLoading
mmm_exec setSupportLink https://github.com/Fox2Code/FoxMagiskModuleManager
```

You may look at the [example module](example_module) code or 
download the [module zip](example_module.zip) and try it yourself

Have fun with the API making the user install experience a unique experience

Also there is the source of the app icon
[here](https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html#foreground.type=clipart&foreground.clipart=extension&foreground.space.trim=0&foreground.space.pad=0.25&foreColor=rgb(255%2C%20255%2C%20255)&backColor=rgb(255%2C%20152%2C%200)&crop=0&backgroundShape=circle&effects=elevate&name=ic_launcher)
.
