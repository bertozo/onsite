import { describe, expect, it } from "vitest";
import {
  abnOk,
  clientErrors,
  isValidAbn,
  isValidAccountNumber,
  isValidBsb,
  isValidEmail,
  isValidPhone,
  parseAmount,
  profileErrors,
} from "./validators";

describe("isValidAbn (ATO modulus-89, same as Validators.kt)", () => {
  it("accepts the ATO's example ABN, with or without spaces", () => {
    expect(isValidAbn("51824753556")).toBe(true);
    expect(isValidAbn("51 824 753 556")).toBe(true);
  });

  it("rejects a wrong check digit, wrong length and non-digits", () => {
    expect(isValidAbn("51824753557")).toBe(false);
    expect(isValidAbn("11111111111")).toBe(false);
    expect(isValidAbn("5182475355")).toBe(false);
    expect(isValidAbn("51-824-753-556")).toBe(false);
  });

  it("treats blank as valid only through the optional variant", () => {
    expect(isValidAbn("")).toBe(false);
    expect(abnOk("")).toBe(true);
    expect(abnOk("   ")).toBe(true);
  });
});

describe("bank details", () => {
  it("BSB is 6 digits, dash or space allowed", () => {
    expect(isValidBsb("062000")).toBe(true);
    expect(isValidBsb("062-000")).toBe(true);
    expect(isValidBsb("062 000")).toBe(true);
    expect(isValidBsb("06200")).toBe(false);
    expect(isValidBsb("062.000")).toBe(false);
  });

  it("account number is 6 to 10 digits", () => {
    expect(isValidAccountNumber("123456")).toBe(true);
    expect(isValidAccountNumber("1234567890")).toBe(true);
    expect(isValidAccountNumber("12345")).toBe(false);
    expect(isValidAccountNumber("12345678901")).toBe(false);
    expect(isValidAccountNumber("12345a")).toBe(false);
  });
});

describe("contact", () => {
  it("phone allows + and separators, needs 8-15 digits", () => {
    expect(isValidPhone("0400 123 456")).toBe(true);
    expect(isValidPhone("+61 (4) 0012-3456")).toBe(true);
    expect(isValidPhone("1234567")).toBe(false);
    expect(isValidPhone("04001234567890123")).toBe(false);
    expect(isValidPhone("0400x123456")).toBe(false);
  });

  it("email needs a domain with a dot", () => {
    expect(isValidEmail("a@b.co")).toBe(true);
    expect(isValidEmail("  a@b.co ")).toBe(true);
    expect(isValidEmail("a@b")).toBe(false);
    expect(isValidEmail("ab.co")).toBe(false);
  });
});

describe("parseAmount", () => {
  it("accepts up to two decimals with . or , and rejects the rest", () => {
    expect(parseAmount("50")).toBe(50);
    expect(parseAmount("50.5")).toBe(50.5);
    expect(parseAmount("50,25")).toBe(50.25);
    expect(parseAmount("50.123")).toBeNull();
    expect(parseAmount("-5")).toBeNull();
    expect(parseAmount("abc")).toBeNull();
    expect(parseAmount("")).toBeNull();
  });
});

describe("form error maps", () => {
  it("reports each invalid profile field and nothing for a blank optional one", () => {
    const errors = profileErrors({ abn: "123", bankBsb: "", bankAccount: "12", phone: "abc", email: "x@y.z" });
    expect(Object.keys(errors).sort()).toEqual(["abn", "accountNumber", "phone"]);
    expect(profileErrors({ abn: "", bankBsb: "", bankAccount: "", phone: "", email: "" })).toEqual({});
  });

  it("reports client fields including the hourly rate", () => {
    expect(clientErrors({ abn: "51824753556", phone: "0400123456", email: "", hourlyRate: "12.345" })).toEqual({
      hourlyRate: "Informe um valor válido",
    });
  });
});
