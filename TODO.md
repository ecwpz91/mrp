# TODO

## Earth Date picker — iOS Safari UX (paused)

**Status:** Parked for later. Android works; iOS Safari still has several pain points with the green **Earth Date** control on `/rovers`.

**Context:** The control uses a native `<input type="date">` overlaid on a styled button (`min` = landing date, `max` = max date). That was enough for Android and for opening the calendar on iOS, but the iOS date UI itself is still awkward for volunteers.

### Observed issues (iOS / Safari)

1. **Out-of-range dates still selectable**  
   Users can pick dates before the mission launch (and possibly outside the landing→max window). Native `min`/`max` are not reliably enforced in Safari’s date wheels the way they are on Android.

2. **Partial selection submits too early**  
   Changing year or month (without finishing on a day) can fire `change` and navigate with a previous/incomplete value. The app then loads a photo for whatever was already in the input instead of waiting for a full year+month+day choice.

3. **Error / constraint messaging blocks the calendar**  
   Messages that tell the user they can’t pick past a given date (browser validation and/or our “no hazcam photos…” errors) sit on top of or interfere with the calendar UI, making selection harder.

### Ideas for a future enhancement

- Prefer an explicit **Confirm** (or only submit on `blur` / form submit), not auto-nav on every `change` — especially for iOS wheels.
- Enforce landing/launch→max date in **JS before navigate** (and optionally on the server) even if Safari ignores `min`/`max`.
- Consider a custom / library date picker with clearer mobile UX, or a two-step flow: open calendar → review chosen date → “View photo”.
- Surface range hints **beside** the button (e.g. “Landing–Max date”) instead of modal/blocking validation chrome over the picker.
- Add a light device test checklist: iOS Safari, Android Chrome, desktop Chrome/Firefox.

### Related files

- `src/main/resources/templates/RoverResource/rovers.html` — Earth Date form + change handler  
- `src/main/resources/META-INF/resources/css/mrp.css` — `.mrp-date-pick*` styles  
- `src/main/java/com/redhat/mrp/web/RoverResource.java` — `earthDate` → `pickPhotoOnOrBefore`
