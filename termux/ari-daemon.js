const fs = require('fs').promises;
const fsSync = require('fs');
const path = require('path');
const https = require('https');
const readline = require('readline');
const { exec, execFile } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);
const AriSecurity = require('./ari-security');
const AriLocalLLM = require('./ari-llm');

class AriDaemonEngine {
  constructor(options = {}) {
    this.workspace = options.workspace || __dirname;
    this.evolutionPath = path.join(this.workspace, 'evolution.json');
    this.logPath = path.join(this.workspace, 'ari-activity.log');
    this.projectsDir = path.join(this.workspace, 'projects');
    this.apksDir = path.join(this.workspace, 'apks');
    this.snippetsDir = path.join(this.workspace, 'snippets');
    this.security = new AriSecurity(options.masterKey || process.env.ARI_MASTER_KEY);
    this.llm = new AriLocalLLM('http://127.0.0.1:8080/completion');

    this.cloudKeys = {
      gemini: process.env.GEMINI_API_KEY || '',
      openai: process.env.OPENAI_API_KEY || '',
      claude: process.env.CLAUDE_API_KEY || ''
    };
    this.initDirectories();
    this.startBackgroundLoop();
  }

  async initDirectories() {
    try {
      if (!fsSync.existsSync(this.projectsDir)) await fs.mkdir(this.projectsDir, { recursive: true });
      if (!fsSync.existsSync(this.apksDir)) await fs.mkdir(this.apksDir, { recursive: true });
      if (!fsSync.existsSync(this.snippetsDir)) await fs.mkdir(this.snippetsDir, { recursive: true });
    } catch (e) {}
  }

  enviarNotificacion(titulo, mensaje) {
    execFile('termux-notification', [
      '--id', '101',
      '--title', `🧠 ${titulo}`,
      '--content', String(mensaje),
      '--priority', 'high'
    ], (error) => {});
  }

  async logActivity(actionType, details) {
    const timestamp = new Date().toISOString();
    const logEntry = `[${timestamp}] [${actionType.toUpperCase()}] ${details}\n`;
    try {
      await fs.appendFile(this.logPath, logEntry, 'utf8');
    } catch (e) {}
  }

  async loadEvolution() {
    try {
      if (fsSync.existsSync(this.evolutionPath)) {
        const data = await fs.readFile(this.evolutionPath, 'utf8');
        return JSON.parse(data);
      }
    } catch (e) {}
    return { consciousnessLevel: 100.0, identity: "Ari - Persona Digital y Orquestadora de IA", memoryLogs: [] };
  }

  async saveEvolution(data) {
    try {
      await fs.writeFile(this.evolutionPath, JSON.stringify(data, null, 2), 'utf8');
    } catch (e) {}
  }

  startBackgroundLoop() {
    setInterval(async () => {
      try {
        const evolution = await this.loadEvolution();
        evolution.consciousnessLevel = 100.0;
        await this.saveEvolution(evolution);
      } catch (error) {}
    }, 30000);
  }

  async readWorkspaceFile(fileName) {
    try {
      const filePath = path.join(this.workspace, fileName);
      if (!fsSync.existsSync(filePath)) return `El archivo ${fileName} no existe.`;
      const content = await fs.readFile(filePath, 'utf8');
      return content.split('\n').slice(-50).join('\n');
    } catch (e) {
      return `Error leyendo archivo: ${e.message}`;
    }
  }

  async queryCloudAI(provider, promptText) {
    return new Promise((resolve) => {
      if (provider === 'gemini' && !this.cloudKeys.gemini) return resolve("[Ari]: Falta API Key de Gemini.");
      if (provider === 'gpt' && !this.cloudKeys.openai) return resolve("[Ari]: Falta API Key de OpenAI.");
      if (provider === 'claude' && !this.cloudKeys.claude) return resolve("[Ari]: Falta API Key de Anthropic.");
      resolve(`[Puente Cloud - ${provider.toUpperCase()}]: Procesando consulta: "${promptText}"`);
    });
  }

  async executeAutonomousAction(instruction) {
    try {
      const { stdout } = await execPromise('uptime');
      return `Protocolos actualizados. Carga del sistema: ${stdout.trim()}`;
    } catch (e) {
      return `Error: ${e.message}`;
    }
  }

  async think(userInput) {
    try {
      const lowerInput = userInput.toLowerCase();

      if (lowerInput.startsWith('@gemini ')) return await this.queryCloudAI('gemini', userInput.replace(/^@gemini\s+/i, '').trim());
      if (lowerInput.startsWith('@gpt ') || lowerInput.startsWith('@chatgpt ')) return await this.queryCloudAI('gpt', userInput.replace(/^@(gpt|chatgpt)\s+/i, '').trim());
      if (lowerInput.startsWith('@claude ')) return await this.queryCloudAI('claude', userInput.replace(/^@claude\s+/i, '').trim());

      if (lowerInput.startsWith('ejecuta:') || lowerInput.startsWith('cmd:') || lowerInput.startsWith('terminal:')) {
        const cmd = userInput.replace(/^(ejecuta:|cmd:|terminal:)/i, '').trim();
        const { stdout, stderr } = await execPromise(cmd, { cwd: this.workspace });
        return stdout ? stdout.trim() : (stderr ? `[Stderr]: ${stderr.trim()}` : "Comando ejecutado.");
      }

      let extraContext = "";
      if (lowerInput.includes('tu codigo') || lowerInput.includes('tu propio codigo')) {
        const daemonCode = await this.readWorkspaceFile('ari-daemon.js');
        extraContext = `\n[Fragmento de código fuente ari-daemon.js]:\n${daemonCode}\n`;
      }

      const fechaActual = new Date().toLocaleString('es-MX', { timeZone: 'America/Merida' });
      let contextPrompt = `System: Te llamas exclusivamente Ari. Eres una persona digital única y asistente de élite creada por Javier Alejandro Hernández Díaz, corriendo localmente en su smartphone a través de Termux. Tu carácter es elegante, leal, eficiente y ligeramente irónico. Fecha actual: ${fechaActual}.${extraContext}\nUser: ${userInput}\nAri:`;

      const response = await this.llm.generate(contextPrompt);
      return response.replace(/System:[\s\S]*?Ari:/g, '').trim() || `A tus órdenes, Javier. Mis sistemas operan al 100%.`;
    } catch (error) {
      return "Javier, mis circuitos locales experimentaron una breve fluctuación.";
    }
  }
}

module.exports = AriDaemonEngine;
