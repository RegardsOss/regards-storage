/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.storage.plugins.datastorage.validation;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import fr.cnes.regards.modules.storage.plugin.datastorage.validation.FileSizeValidator;

/**
 * @author Sylvain Vissiere-Guerinet
 *
 */
public class FileSizeValidatorTest {

    private FileSizeValidator fileSizeValidator;

    @Before
    public void init() {
        fileSizeValidator = new FileSizeValidator();
    }

    @Test
    public void testValid() {
        int size = 1024;
        String kb = "kb";
        Assert.assertTrue(fileSizeValidator.isValid(size + kb, null));
        String Kb = "Kb";
        Assert.assertTrue(fileSizeValidator.isValid(size + Kb, null));
        String kbits = "kbits";
        Assert.assertTrue(fileSizeValidator.isValid(size + kbits, null));
        String Kbits = "Kbits";
        Assert.assertTrue(fileSizeValidator.isValid(size + Kbits, null));
        String kB = "kB";
        Assert.assertTrue(fileSizeValidator.isValid(size + kB, null));
        String KB = "KB";
        Assert.assertTrue(fileSizeValidator.isValid(size + KB, null));
        String kBytes = "kBytes";
        Assert.assertTrue(fileSizeValidator.isValid(size + kBytes, null));
        String KBytes = "KBytes";
        Assert.assertTrue(fileSizeValidator.isValid(size + KBytes, null));
        String kib = "kib";
        Assert.assertTrue(fileSizeValidator.isValid(size + kib, null));
        String Kib = "Kib";
        Assert.assertTrue(fileSizeValidator.isValid(size + Kib, null));
        String kiB = "kiB";
        Assert.assertTrue(fileSizeValidator.isValid(size + kiB, null));
        String KiB = "KiB";
        Assert.assertTrue(fileSizeValidator.isValid(size + KiB, null));
    }

    @Test
    public void testInvalid() {
        int size = 1024;
        String kgb = "kgb";
        Assert.assertFalse(fileSizeValidator.isValid(size + kgb, null));
        String KGb = "KGb";
        Assert.assertFalse(fileSizeValidator.isValid(size + KGb, null));
        String kgB = "kgB";
        Assert.assertFalse(fileSizeValidator.isValid(size + kgB, null));
        String KGB = "KGB";
        Assert.assertFalse(fileSizeValidator.isValid(size + KGB, null));
        String kgib = "kgib";
        Assert.assertFalse(fileSizeValidator.isValid(size + kgib, null));
        String KGib = "KGib";
        Assert.assertFalse(fileSizeValidator.isValid(size + KGib, null));
        String kgiB = "kgiB";
        Assert.assertFalse(fileSizeValidator.isValid(size + kgiB, null));
        String KGiB = "KGiB";
        Assert.assertFalse(fileSizeValidator.isValid(size + KGiB, null));
    }

}