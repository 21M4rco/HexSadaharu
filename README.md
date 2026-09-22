# Hex Sadaharu

A standalone **Minecraft Forge 1.20.1 / Java 17** companion mod. No additional gameplay libraries are required. Compile only through this repository's GitHub Actions workflow.

## Playing

* `/hs spawn` (operator permission 2) introduces the single world companion. Repeating the command will not create another.
* Offer **2–6 bones** to tame him. The random threshold is selected once and survives reloads.
* Craft four **Kibble** from one meat, one fish and wheat, shapeless. Optional Forge tags accept compatible modded ingredients.
* The owner’s screen carries a follow toggle: **Following you** keeps him within about eight blocks, **Staying put** leaves him to his own devices. Sending him home turns following off, since they are opposite instructions.
* Owner right-click opens a compact home/settings screen. **Shift + right-click** or **Ride Sadaharu** mounts him.
* **Send him home**, on that screen once a home is set, dismisses him politely: he walks off on his own feet and only crosses over once he is out of your sight, then keeps to the area around home for about ten minutes.
* Ride with normal movement controls. Sprint to accelerate further; charge/release the normal mount jump control for a major leap.
* **H — Call Dog**, remappable in Controls, is the only added key. Only the owner can call him.

## Design

The model is original cuboid geometry rather than a vanilla wolf model: 69 bones and 315 cuboids built as stacked superellipse layers with chamfered corners, so the silhouette curves instead of stepping. A round head with low soft brows set close over round eyes, a short muzzle whose jaw closes flush with no teeth and nothing dark exposed, working eyelids, a collar band laid on an ellipse around the throat, a layered chest ruff, tapered legs and a four-part curled tail. The native model controller layers breathing, a nose twitch, a weight shift, a drifting idle gaze, ear and tail motion and gait under 51 timed gestures, plus continuous swimming and airborne poses with smooth resting transitions. Blinking rotates the eyelids shut about every four seconds, sometimes twice.

Sadaharu makes server-side decisions using bounded awareness caches and persisted memory. Protection is a single maximum-2-health hit followed by retreat and a 45-second cooldown. Hunger changes behavior, never health; a full meter takes roughly 100 minutes to empty. Home return is tempered by distance, danger and travel with the owner. He leaps walls and gaps on his own rather than only stepping up: his jump clears roughly two blocks, against a vanilla 0.42 that barely beats his own 1.1-block step. Deep water outranks leisure — he paddles to the owner if they are close, otherwise to the nearest shore — and water is no longer priced so high in pathfinding that he walks around every pond. Asleep, he breathes deeply with his eyes shut and drops into occasional dream twitches that return him to sleep rather than waking him. Ordinary behavior never breaks blocks. Casual interaction with people he knows is part of the idle layer: he closes the distance and mouths a player or villager over the head without damage or taking control of them, licks the owner at the face on his own initiative, and sulks with flattened ears and a lowered head when he is hungry or when a threat is still on his intervention cooldown.

The overworld SavedData record reserves his UUID even while unloaded. Calling tickets the recorded chunk, waits for its existing entity to load, then transfers that entity, including between dimensions. It never spawns a replacement. If the saved chunk or destination is unavailable, the call reports the issue. Damage/death guards and a narrowly scoped removal override protect him; chunk unloading remains legal.

## Build and checks

**Actions → Build and verify Sadaharu** compiles and packages the JAR/source ZIP, validates resources, runs Forge GameTests and renders the model through the real Minecraft client. Download the artifact from a successful run. No compilation was performed in the authoring workspace.

`tools/verify.py` also reconstructs the rig's rest pose and asserts that every mouth-interior and eyelid cuboid is fully enclosed by fur. A muzzle that stops closing, or a lid that pokes through the skull, fails the build rather than waiting to be noticed in a screenshot.

`tools/make_assets.py` authors the rig and item pixel art; `tools/render_model.py` produces a depth-rendered contact sheet from the exact rig. These optional tools need Python, Pillow and NumPy. They do not compile Java.

[Geometry review](docs/model-review.png)

### Reference sources

* [TV Tokyo official Sadaharu character artwork](https://www.tv-tokyo.co.jp/anime/gintama/chara/yorozu/)
* [Official Gintama anniversary riding illustration](https://www.anime-gintama.com/images/common/chara-03.png)
* [Bandai Channel anime profile/raised-tail frame](https://image2.b-ch.com/ttl2/3653/3653029a.jpg)
* [TVer anime side-profile/curl frame](https://tver.jp/episodes/epz733o0bh)

Reference images are not bundled. All model geometry, textures and code are authored for this mod. Every bark he has — the ordinary bark, the deep bark and the excited greeting — is one supplied recording of his voice, converted to mono Ogg Vorbis and repitched per event; `tools/verify.py` fails the build if it is missing, stops being his own voice, or is not mono, since a stereo file would play at full volume across the whole world instead of attenuating with distance. His other vocalizations use the installed Minecraft sound library.

## Manual acceptance checks

Automated server checks are not a substitute for a play session. Verify the front/side/rear silhouette, riding seat and charged leap under latency, head alignment with differently sized humanoids, ten-minute autonomous behavior, unloaded-chunk calls, restart persistence and cross-dimensional calls in the intended modpack. Arbitrary third-party code that rewrites the world's saved files is outside entity-level protection.
