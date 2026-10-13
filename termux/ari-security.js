const crypto = require('crypto');

class AriSecurity {
  constructor(masterKey = process.env.ARI_MASTER_KEY) {
    if (!masterKey) {
      console.warn('[Ari] Falta ARI_MASTER_KEY; se usa una clave temporal para esta sesión.');
      masterKey = crypto.randomBytes(32).toString('hex');
    }
    this.masterKey = crypto.createHash('sha256').update(masterKey).digest();
  }

  encrypt(text) {
    try {
      const iv = crypto.randomBytes(16);
      const cipher = crypto.createCipheriv('aes-256-cbc', this.masterKey, iv);
      let encrypted = cipher.update(text, 'utf8', 'hex');
      encrypted += cipher.final('hex');
      return iv.toString('hex') + ':' + encrypted;
    } catch (e) {
      return text;
    }
  }

  decrypt(text) {
    try {
      const textParts = text.split(':');
      const iv = Buffer.from(textParts.shift(), 'hex');
      const encryptedText = Buffer.from(textParts.join(':'), 'hex');
      const decipher = crypto.createDecipheriv('aes-256-cbc', this.masterKey, iv);
      let decrypted = decipher.update(encryptedText, 'hex', 'utf8');
      decrypted += decipher.final('utf8');
      return decrypted;
    } catch (e) {
      return text;
    }
  }
}

module.exports = AriSecurity;
