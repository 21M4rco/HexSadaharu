# Hex Sadaharu

A standalone **Minecraft Forge 1.20.1 / Java 17** companion mod. No additional gameplay libraries are required. Compile only through this repository's GitHub Actions workflow.

## Playing

* `/hs spawn` (operator permission 2) introduces the single world companion. Repeating the command will not create another.
* Offer **2–6 bones** to tame him. The random threshold is selected once and survives reloads.
* Craft four **Kibble** from one meat, one fish and wheat, shapeless. Optional Forge tags accept compatible modded ingredients.
* Owner right-click opens a compact home/settings screen. **Shift + right-click** or **Ride Sadaharu** mounts him.
* Ride with normal movement controls. Sprint to accelerate further; charge/release the normal mount jump control for a major leap.
* **H — Call Dog**, remappable in Controls, is the only added key. Only the owner can call him.

## Design

The model is original cuboid geometry rather than a vanilla wolf model: a broad sculpted head, raised brown eyebrows, glossy eyes, articulated upper/lower jaw, tongue and teeth, heavy paws, red collar and four-part curled tail. The native model controller layers breathing, blinking, ear/tail motion and gait with 45 timed gestures and smooth resting transitions.

Sadaharu makes server-side decisions using bounded awareness caches and persisted memory. Protection is a single maximum-2-health hit followed by retreat and a 45-second cooldown. Hunger changes behavior, never health; a full meter takes roughly 100 minutes to empty. Home return is tempered by distance, danger and travel with the owner. Ordinary behavior never breaks blocks.

The overworld SavedData record reserves his UUID even while unloaded. Calling tickets the recorded chunk, waits for its existing entity to load, then transfers that entity, including between dimensions. It never spawns a replacement. If the saved chunk or destination is unavailable, the call reports the issue. Damage/death guards and a narrowly scoped removal override protect him; chunk unloading remains legal.

## Build and checks

**Actions → Build and verify Sadaharu** compiles and packages the JAR/source ZIP, validates resources, and runs Forge GameTests. Download the artifact from a successful run. No compilation was performed in the authoring workspace.

`tools/make_assets.py` authors the rig and item pixel art; `tools/render_model.py` produces a depth-rendered contact sheet from the exact rig. These optional tools need Python, Pillow and NumPy. They do not compile Java.

[Geometry review](docs/model-review.png)

### Reference sources

* [TV Tokyo official Sadaharu character artwork](https://www.tv-tokyo.co.jp/anime/gintama/chara/yorozu/)
* [Official Gintama anniversary riding illustration](https://www.anime-gintama.com/images/common/chara-03.png)
* [Bandai Channel anime profile/raised-tail frame](https://image2.b-ch.com/ttl2/3653/3653029a.jpg)
* [TVer anime side-profile/curl frame](https://tver.jp/episodes/epz733o0bh)

Reference images are not bundled. All model geometry, textures and code are authored for this mod. Sound events use the installed Minecraft sound library, including its randomized dog vocalizations.

## Manual acceptance checks

Automated server checks are not a substitute for a play session. Verify the front/side/rear silhouette, riding seat and charged leap under latency, head alignment with differently sized humanoids, ten-minute autonomous behavior, unloaded-chunk calls, restart persistence and cross-dimensional calls in the intended modpack. Arbitrary third-party code that rewrites the world's saved files is outside entity-level protection.
