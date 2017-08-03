/*---------------------------------------------------------------
*  Copyright 2005 by the Radiological Society of North America
*
*  This source software is released under the terms of the
*  RSNA Public License (http://mirc.rsna.org/rsnapubliclicense)
*----------------------------------------------------------------*/

package org.rsna.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.security.SecureRandom;

/**
 * A class providing a decoder for encrypted elements in a DICOM file.
 */
public class CipherUtil {

  static final String transform = "Blowfish";

  /**
   * Get the maximum key length allowed by the Java Cryptography Extension.
   * 
   * @return the maximum allowed key length, or zero if the transform is not
   *         available.
   */
  public static int getMaxAllowedKeyLength() {
    return getMaxAllowedKeyLength(transform);
  }

  /**
   * Get the maximum key length allowed by the Java Cryptography Extension.
   * 
   * @param name
   *        the name of the transform.
   * @return the maximum allowed key length, or zero if the transform is not
   *         available.
   */
  public static int getMaxAllowedKeyLength(String name) {
    try {
      return Cipher.getMaxAllowedKeyLength(name);
    } catch (Exception ex) {
      return 0;
    }
  }

  /**
   * Encrypt a string.
   * 
   * @param text
   *        the base-64 text representation of the encrypted UTF-8 string.
   * @param key
   *        the encryption key as a Base-64 string.
   * @return the encrypted string in Based-64.
   */
  public String encrypt(String text, String key) throws Exception {
    if (text == null)
      text = "null";
    Cipher enCipher = getCipher(key, Cipher.ENCRYPT_MODE);
    byte[] encrypted = enCipher.doFinal(text.getBytes("UTF-8"));
    return Base64.encodeToString(encrypted);
  }

  /**
   * Decrypt an encrypted string.
   * 
   * @param text
   *        the base-64 text representation of the encrypted UTF-8 string.
   * @param key
   *        the encryption key as a Base-64 string.
   * @return the decrypted string.
   */
  public static String decrypt(String text, String key) throws Exception {
    Cipher cipher = getCipher(key.trim(), Cipher.DECRYPT_MODE);
    byte[] encrypted = Base64.decode(text);
    byte[] decrypted = cipher.doFinal(encrypted);
    return new String(decrypted, "UTF-8");
  }

  // Get a Cipher initialized with the specified key.
  private static Cipher getCipher(String keyText, int mode) {
    try {
      // Provider sunJce = new com.sun.crypto.provider.SunJCE();
      // Security.addProvider(sunJce);
      byte[] key = getEncryptionKey(keyText, 128);
      SecretKeySpec skeySpec = new SecretKeySpec(key, transform);

      SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
      byte[] seed = random.generateSeed(8);
      random.setSeed(seed);

      Cipher cipher = Cipher.getInstance(transform);
      cipher.init(mode, skeySpec, random);
      return cipher;
    } catch (Exception ex) {
      return null;
    }
  }

  // Make an encryption key from a string
  static String junk = "tszyihnnphlyeaglle";
  static String pad = "===";

  private static byte[] getEncryptionKey(String keyText, int size) throws Exception {
    keyText = (keyText == null) ? "" : keyText.trim();

    // Now make it into a base-64 string encoding the right number of bits.
    keyText = keyText.replaceAll("[^a-zA-Z0-9+/]", "");

    // Figure out the number of characters we need.
    int requiredChars = (size + 5) / 6;
    int requiredGroups = (requiredChars + 3) / 4;
    int requiredGroupChars = 4 * requiredGroups;

    // Pad the keyText if necessary.
    if (keyText.length() < requiredChars) {
      StringBuffer sb = new StringBuffer(keyText);
      while (sb.length() < requiredChars)
        sb.append(junk);
      keyText = sb.toString();
    }

    // Take just the right number of characters for the size.
    keyText = keyText.substring(0, requiredChars);

    // And return the string padded to a full group.
    keyText = (keyText + pad).substring(0, requiredGroupChars);
    return Base64.decode(keyText);
  }

}
