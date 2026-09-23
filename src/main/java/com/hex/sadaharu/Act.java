package com.hex.sadaharu;
/** Timed, synchronized full-body gestures. Motion and micro-idles are independent layers. */
public enum Act {
    NONE(0), LOOK(100), TILT_LEFT(65), TILT_RIGHT(65), SNIFF_AIR(65), SNIFF_GROUND(90), SCRATCH(110), SHAKE(40), STRETCH_FRONT(95), STRETCH(110), SIT(220), SIT_PANT(200), LIE(250), CHIN(240), SIDE(180), SLEEP(1200), WAKE(50), YAWN(65), PANT(110), LICK_NOSE(35), LICK_PAW(100), PAW(80), HOP(30), ALERT(70), STARE(110), GROWL(55), BARK(30), WHINE(55), BITE(24), HEAD_BITE(100), EAT(90), DRINK(110), POOP(100), MOUNT(35), PREPARE_LEAP(8), LEAP(40), LAND(16), EAR_LEFT(30), EAR_RIGHT(30), EAR_BOTH(30), SLEEP_TWITCH(50), WAG_SLOW(160), WAG_EXCITED(90), EAR_FLICK(20), HEAD_SHAKE(34), SNEEZE(30), LOOK_UP(70), PLAY_BOW(75), TAIL_CHASE(70), LICK_PLAYER(70), POUT(95), DOWNED(220), NUZZLE(85), OFFER_PAW(95), PETTED(100), SNIFF_TRAIL(120), LOOK_BACK(55), POUNCE(45);
    public final int ticks;
    Act(int ticks) { this.ticks=ticks; }
    public boolean resting() { return this==SIT||this==SIT_PANT||this==LIE||this==CHIN||this==SIDE||this==SLEEP||this==SLEEP_TWITCH; }
}
