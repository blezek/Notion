package edu.mayo.qia.pacs.components;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.shiro.crypto.hash.Sha512Hash;
import org.apache.shiro.util.ByteSource;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

@Entity
@Table(name = "USERS")
public class User {

  @Id
  @Column
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public int userKey;

  public String username;
  public String email = "";

  public String uid = UUID.randomUUID().toString();
  public Boolean activated = false;
  public Boolean isAdmin = false;
  public String activationHash = UUID.randomUUID().toString();

  // Never pass password and salt out
  @JsonIgnore
  public String password;
  @JsonIgnore
  public String salt;

  public User() {
  }

  public void setPassword(String password, int hashIterations) {
    Random rng = new SecureRandom();

    String salt = Long.toString(rng.nextLong());
    this.password = new Sha512Hash(password, ByteSource.Util.bytes(salt), hashIterations).toHex();
    // save the salt with the new account. The HashedCredentialsMatcher
    // will need it later when handling login attempts:
    this.salt = salt;
  }

}
