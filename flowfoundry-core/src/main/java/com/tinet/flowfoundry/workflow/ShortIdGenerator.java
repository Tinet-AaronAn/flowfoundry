package com.tinet.flowfoundry.workflow;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class ShortIdGenerator {

  private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
  private static final int LENGTH = 8;

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    char[] value = new char[LENGTH];
    for (int i = 0; i < LENGTH; i++) {
      value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    return new String(value);
  }
}
