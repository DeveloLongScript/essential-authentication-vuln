# essential-authentication-vuln
Hello. If you have ever played modern moddded Minecraft (using Fabric or Quilt), you might have heard of the mod by SparkUniverse named Essential. *Essential* is a mod for communication and hosting/sharing Minecraft worlds, with no technical knowledge required. You simply add it to the mods folder of your game, or install it through Essential's installer, downloadable from its website. Then you don't think about it. But brewing behind the scenes, a terrible vulnerbility affecting Essentials authentication.

## Basis
This vulnerbility is based on Essentials, `ConnectionManager` file, responsible for the authentication and connection into Essentials websocket network. However, the authentication required, is a **big problem**. Essential is authenticating using the following items, your Minecraft username, and a random number generator. (aka Java's `SecureRandom` class) I realized this, and immediately thought, who in there right mind is authenticating with **NOTHING** sensitive. And then Essential says, oh we focus on player safety!

> Absolutely! At Essential, **user safety** and privacy are paramount:

taken from [Essentials wiki](https://sparkuniverse.notion.site/ESSENTIAL-Wiki-189ca7f703c6449e802ef1429070fa4e) under the question *Is Essential safe?* in the FAQ

## Aftermath of being targeted
Everything, (messages, hosted worlds, friend requests etc.) ever stored from Essential is under your username that anyone can now get through. Also their is no way to protect against it, all of your messages are no longer safe.

## But you decompiled the code!

> With limiting the foregoing, while using the Platform, you may not:
> - ...
> - Decompile, reverse engineer, or disassemble any software or other products or processes accessible through the Platform;

Uhh, yes I did. But does it look like I care anymore because of how unsafe the Essential mod is? If you'd like to see, the whole 2 files related are in the source of this GitHub.

## What should you do?
Well, if someone got into your Essential account, they cannot open servers, etc. etc. however they still can message and accept friend requests on your account. They also can gift cosmetics to other accounts using your Essential coins. So, really if you would like to switch away from Essential, thats fine. Just remember all people need is a username!

## Conclusion
I'm kinda amazed at something like this in a **CORPORATE** setting is happening. This isn't any other developer on the street, this is SparkUniverse we are talking about here. They just didn't put any effort at all to making sure the authentication system is secure.
