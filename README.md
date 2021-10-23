# Fox's Magisk Module Manager

The official Magisk is dropping support to download online modules...  
So I made my own app to do that! :3

**This app is not officially supported by Magisk or it's developers**

## Requirements

Minimum:
- Android 5.0+
- Magisk 19.0+

Recommended:
- Android 6.0+
- Magisk 21.2+

## For users

Related commits:  
- [`Remove online section in modules fragment`](https://github.com/topjohnwu/Magisk/commit/f5c982355a2e3380b2b64af4b0caa8f4f7cf9157)
- [`Cleanup unused code`](https://github.com/topjohnwu/Magisk/commit/8d59caf635591eb23813d75601039bb138f5716b)

Note: These changes didn't hit canary, beta, or release yet.

The app currently use these two repo as their modules sources:  
[https://github.com/Magisk-Modules-Alt-Repo](https://github.com/Magisk-Modules-Alt-Repo)  
[https://github.com/Magisk-Modules-Repo](https://github.com/Magisk-Modules-Repo)

As the main repo may shutting down due to the main app no longer supporting it.  
I recommend submitting your modules [here](https://github.com/Magisk-Modules-Alt-Repo/submission) instead

If a module is in both repo, the manager will just pick the most up to date version of the module

## For developers

The manager can read new meta keys to allow modules to customize their own entry

It also use `minApi`, `maxApi` and `minMagisk` in the `module.prop` to detect compatibility  
And support the `support` and `donate` properties to allow them to add their own support links  
(Note: the manager use fallback values for some modules, see developer documentation for more info)

It also add new ways to control the installer ui via a new `#!` command system  
It allow module developers to have a more customizable install experience

For more information please check the [developer documentation](DEVELOPERS.md)

## Screenshots

Main activity:  
[<img src="screenshot.jpg" width="250"/>](screenshot.jpg)
