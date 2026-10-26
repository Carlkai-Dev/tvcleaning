package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** wallpaper_on_return: a return to Home replaces the picture, the first start and a single picture do not. */
public class WallpaperOnReturnTest {

    @Test
    public void offChangesNothing() {
        assertFalse(WallpaperPlayer.newPictureOnReturn(false, false, 5));
        assertFalse(WallpaperPlayer.newPictureOnReturn(false, true, 5));
    }

    @Test
    public void theFirstStartKeepsTheOpeningPicture() {
        assertFalse(WallpaperPlayer.newPictureOnReturn(true, false, 5));
    }

    @Test
    public void aReturnBringsANewPicture() {
        assertTrue(WallpaperPlayer.newPictureOnReturn(true, true, 2));
        assertTrue(WallpaperPlayer.newPictureOnReturn(true, true, 15));
    }

    @Test
    public void nothingToChangeToWithOneOrNoPicture() {
        assertFalse(WallpaperPlayer.newPictureOnReturn(true, true, 1));
        assertFalse(WallpaperPlayer.newPictureOnReturn(true, true, 0));
    }
}
