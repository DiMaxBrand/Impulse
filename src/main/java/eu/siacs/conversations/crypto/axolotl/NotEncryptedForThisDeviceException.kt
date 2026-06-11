package eu.siacs.conversations.crypto.axolotl

class NotEncryptedForThisDeviceException : CryptoFailedException("Message was not encrypted for this device")
