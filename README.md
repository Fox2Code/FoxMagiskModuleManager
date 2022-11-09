# Fox's Magisk Module Manager

<details>
<summary>
  Important news
</summary>

I have health problems that made me work slow on everything.
I don't like sharing my health problmes but it has been to much recently for me to keep it for myself.

This has been too much for me recently, so my moderators (same that on telegram) 
will be handling the project for me for a while.

I had theses problems even before I started FoxMMM, the only reason no one noticed is because 
I can work or go to any school because of how much pain and exhaustion I feel everyday.

The only two thing that helped me reduce the pain is making code and playing with firends.

Even tho I'm very slow at doing anything, the only thing that made me look like I was working 
on this project at a normal speed like someone that work is because 75% of my time was on this project.

There was also some times I couldn't work on this projects for multiple days because of my health,
sometimes I was forcing myself to change one line of code from this project because doing nothing
was more painful that trying something while in pain.

Spending time with my friend and working on this project was a sort of pain killer for me.

Even tho I received money from my parent and the governement for my health problems, 
I didn't know what to do with it cause anything I could have bought had no use for me 
because my extreme pain made me unable to use anything. (Even video games)

My health issues also prevented me to do any project of greater complexcity that this, 
and without community support I would have been physically unable to continue this project.


There was clues of my health problems, right on this project, and theses are the following:
- My commit time of day being random proving I have no jobs.
- Me not commiting for entire week, or having only commited one line in a week.
- Me taking too much time to publish release after I did the relase commit.
- Me missing obvious bugs and being able to do simple task properly
  (Well maybe this last one is harder to proove via commit history)

But sine many peoples are faking health issues for clout, if any data-scientist want 
to do an analysis to proove what it would make my day, and I would be happy to give money
if someone does that because I don't know what do to with my money at this point.

This is really sickening peoples need to give proof for their mental/health 
issues because some peoples fake having thoses issues for clout.

If you want to use my health problems for coult, I don't care as long as you are respectful, 
at least you won't be hurting peoples with mental/health issues by faking having thoses issues.

I'll probably delete this section once my health would be gotten better, or at 
least good enough for me to not be stuck on my bed at least once a day because of pain.

</details>

## Community

[![Telegram Group](https://img.shields.io/endpoint?color=neon&style=flat&url=https%3A%2F%2Ftg.sumanjay.workers.dev%2FFox2Code_Chat)](https://telegram.dog/Fox2Code_Chat)
<a href="https://translate.nift4.org/engage/foxmmm/">
<img src="https://translate.nift4.org/widgets/foxmmm/-/foxmmm/svg-badge.svg" alt="Translation status" />
</a>

## Screenshots

Main activity:  
[<img src="screenshot-dark.jpg" width="250"/>](screenshot-dark.jpg)
[<img src="screenshot-light.jpg" width="250"/>](screenshot-light.jpg)

## What is this? 

The official Magisk has dropped support to download online modules, so I made Fox's Magisk Module Manager to help you download and install Magisk modules.

**This app is not officially supported by Magisk or its developers**

**The modules shown in this app are not affiliated with this app or Magisk**  
(Please contact repo owners instead)

## Requirements

Minimum:
- Android 5.0+
- Magisk 19.0+
- An internet connection

Recommended:
- Android 6.0+
- Magisk 21.2+
- An internet connection

Note: This app may require the use of a VPN in countries with a state wide firewall.

## For users

To install the app go to [releases](https://github.com/Fox2Code/FoxMagiskModuleManager/releases), 
and download and install the latest `.apk` on your device.

## Repositories Available


The app currently use these two repos as module sources, each with their own benefits and drawback:  
(Note: Each module repo can be disabled in the settings of the app)  
(Note²: I do not own or actively monitor any of the repos or modules, **download at your own risk**)

#### [https://github.com/Magisk-Modules-Alt-Repo](https://github.com/Magisk-Modules-Alt-Repo)  
- Accepting new modules [here](https://github.com/Magisk-Modules-Alt-Repo/submission)
- Less restrictive than the original repo
- Officially supported by Fox's mmm

Support:

[![GitHub issues](https://img.shields.io/github/issues/Magisk-Modules-Alt-Repo/submission)](https://github.com/Magisk-Modules-Alt-Repo/submission/issues)

#### [https://www.androidacy.com/modules-repo/](https://www.androidacy.com/modules-repo/)
- Accepting new modules [here](https://www.androidacy.com/module-repository-applications/)
- Modules downloadable easily outside the app
- Officially supported by Fox's mmm
- Contains ads to help cover server costs
- Added features like module reviews, automatic VirusTotal scans, and more

Support:

[![Telegram Group](https://img.shields.io/endpoint?color=neon&style=flat&url=https%3A%2F%2Ftg.sumanjay.workers.dev%2Fandroidacy_discussions)](https://telegram.dog/androidacy_discussions)

If a module is in multiple repos, the manager will just pick the most up to date version 
of the module, if a module is in multiple repos it will just use first registered repo.

Note: If you or a friend uploaded a module and it doesn't appear in your module 
list you can disable the low quality filter in the app settings.  
Go to the [developer documentation](DEVELOPERS.md) for more info.

## For developers

The manager can read new meta keys to allow modules to customize their own entry

It also use `minApi`, `maxApi` and `minMagisk` in the `module.prop` to detect compatibility  
And support the `support` and `donate` properties to allow them to add their own support links  
And if you want to be event fancier you can setup `config` to your own config app.  
(Note: the manager use fallback values for some modules, see developer documentation for more info)

It also add new ways to control the installer ui via a new `#!` command system  
It allow module developers to have a more customizable install experience

For more information please check the [developer documentation](DEVELOPERS.md)

## For translators

We use Weblate for translations: https://translate.nift4.org/engage/foxmmm/
(Make sure to check your spam folder when registering)

If you do not want to register on the self-hosted Weblate instance, you can do a pull request on GitHub:

See [`app/src/main/res/values/strings.xml`](https://github.com/Fox2Code/FoxMagiskModuleManager/blob/master/app/src/main/res/values/strings.xml)
and [`app/src/main/res/values/arrays.xml`](https://github.com/Fox2Code/FoxMagiskModuleManager/blob/master/app/src/main/res/values/arrays.xml)

If your language is right to left don't forget to set `lang_support_rtl` to `true`.

Translators are not expected to have any previous coding experience.

## I want to add my own repo

To add you own repo to Fox's mmm it need to follow theses conditions:
- The module repo or at least one of it's owners must be known.
- Modules in the repo must be monitored, and malicious modules must be removed.
- Module repo must have a valid, working, automatically or frequently updated `modules.json`
  ([Example](https://github.com/Magisk-Modules-Alt-Repo/json/blob/main/modules.json))
  
In addition of these initial condition the repo must follow these rules:
- Repos must process and take-down off their repo module where it's removal was requested
  by their original author, even if their licences legally allow their distributions.
- Repos may collect and store "mixed anonymous data" without user permission
  (Anonymous means no personal data, usernames, email, or IP addresses)
  (Mixed means users data must be split and not that separate data is not linkable together)
- Temporary storage of IPs address without user consent is allowed for rate limiting, GeoIP,
  security reason, and must not be used for any other purpose without user explicit consent.
  (GeoIP is the process of getting the country of an IP address)
- Repos may not collect and/or distribute any personal data without informing users that they do so and offering a way to opt out
- Modules owners must be aware that their modules are being hosted on the repository  
  (This rule doesn't apply for modules from `Magisk-Modules-Repo` last updated before 2022)
- Modules owners must be aware of any change made of the distributed version of their modules.

Please note Androidacy has their Module Repository Policies outlined [on their website](https://www.androidacy.com/module-requirements/?utm_source=foxmmm-readme&utm_medium=web). Please refer to that document for the latest changes regarding their Repository.

If all of these conditions are met you can open an issue for review.  
(And don't forget to include a link to the `modules.json`)

If an existing repo is not respecting theses rules please open an issue.  
If a repo is repeatedly violating these rule will be removed from the app.  
Last update of theses rules are: 4 May 2022

Please note that these rules does not apply retroactively.
If your post an issue about rules violation they must violate both the version of
the rules at the moment of the incident and the latest version of the rules.  
(This paragraph doesn't apply for license violation, legal requests, or illegal behaviour.)

In addition, we advise you to contact the repo host beforehand to attempt to resolve any issues. This helps avoid unnecessary conflict, and most of the time will get your issue solved quickly! 
