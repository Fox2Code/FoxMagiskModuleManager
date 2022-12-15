## Adding custom repositories

You have two options:

### Option 1: Add your repo to the app for yourself:

1. You need the modules.json link of your repo, if you don't have one please contact the repo owner.
2. Open the app and go to the settings.
3. Go to repos at the top.
4. Scroll to the bottom and click on the "Add repo" button.
5. Paste the modules.json link and click on the "Add" button.
6. You can now download modules from your repo.

### Option 2: Add your repo to the app for everyone:

To add you own repo to Fox's mmm it need to follow theses conditions:

- The module repo or at least one of it's owners must be known.
- Modules in the repo must be monitored, and malicious modules must be removed.
- Module repo must have a valid, working, automatically or frequently updated `modules.json`
  ([Example](https://github.com/Magisk-Modules-Alt-Repo/json/blob/main/modules.json))

These guidelines are not mandatory, but not following them may result in your repo being removed or
not being added in the first place.
<details>
<summary>Click to see the guidelines</summary>

- Repos must process and take-down off their repo module where it's removal was provably
  requested by
  their
  original author
- Repos may not collect and/or distribute any personally identifiable data (including IP
  addresses) without
  informing
  users
  that they do so and offering a way to opt out
- Modules owners must be aware that their modules are being hosted on the repository and/or have a
  way to remove their modules from the repository
- Modules owners must be aware of any change made of the distributed version of their modules.
- Repos should make an effort to keep users safe, via a review process, or by using a
  whitelist/blacklist

</details>

In all scenarios, insofar that their policies are not in conflict with the above, the repo owner's
poloicies take precedence over the above guidelines. We encourage users to check the guidelines of
the repo they are using.