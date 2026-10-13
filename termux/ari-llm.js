const http = require('http');

class AriLocalLLM {
  constructor(endpoint = 'http://127.0.0.1:8080/completion') {
    this.endpoint = endpoint;
  }

  async startLocalServer() {
    return new Promise((resolve) => {
      const urlObj = new URL(this.endpoint);
      const req = http.request({
        hostname: urlObj.hostname,
        port: urlObj.port,
        path: urlObj.pathname,
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, (res) => {
        resolve(true);
      });
      req.on('error', () => {
        resolve(false);
      });
      req.write(JSON.stringify({ prompt: "ping", n_predict: 1 }));
      req.end();
    });
  }

  async generate(promptText) {
    return new Promise((resolve, reject) => {
      const urlObj = new URL(this.endpoint);
      const payload = JSON.stringify({
        prompt: promptText,
        n_predict: 256,
        temperature: 0.5,
        top_p: 0.9,
        stop: ["User:", "\n\n\n"]
      });
      const req = http.request({
        hostname: urlObj.hostname,
        port: urlObj.port,
        path: urlObj.pathname,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload)
        }
      }, (res) => {
        let data = '';
        res.on('data', (chunk) => { data += chunk; });
        res.on('end', () => {
          try {
            const parsed = JSON.parse(data);
            resolve(parsed.content || parsed.text || "");
          } catch (e) {
            resolve(data);
          }
        });
      });
      req.on('error', (e) => { reject(e); });
      req.write(payload);
      req.end();
    });
  }
}

module.exports = AriLocalLLM;
