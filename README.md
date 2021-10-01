# Fox's Magisk Module Manager

The official Magisk is dropping support to download online modules...  
So I made my own app to do that!

**This app is not officially supported by Magisk or it's developers**

## For users

Related commits:
- [`Remove online section in modules fragment`](https://github.com/topjohnwu/Magisk/commit/f5c982355a2e3380b2b64af4b0caa8f4f7cf9157)
- [`Cleanup unused code`](https://github.com/topjohnwu/Magisk/commit/8d59caf635591eb23813d75601039bb138f5716b)

The app currently use these two repo as their modules sources:  
[https://github.com/Magisk-Modules-Alt-Repo](https://github.com/Magisk-Modules-Alt-Repo)
[https://github.com/Magisk-Modules-Repo](https://github.com/Magisk-Modules-Repo)

As the main repo may shutting down due to the main app no longer supporting it.  
I recommend submitting your modules [here](https://github.com/Magisk-Modules-Alt-Repo/submission) instead

If a module is in both repo, the manager will just pick the most up to date version of the module

## For developers

The manager add and read new meta keys to modules

It use `module.prop` the `minApi=<int>` and `minMagisk=<int>` properties to detect compatibility  
And use the `support=<url>` and `donate=<url>` key to detect module related links

It also add new ways to control the installer ui via a new command system

For more information please check the [developer documentation](DEVELOPERS.md)

## Screenshots

Main activity:  
[<img src="screenshot.jpg" width="250"/>](screenshot.jpg)
