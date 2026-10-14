import Foundation
import AriCore

/// Descarga el modelo en segundo plano y solo por Wi-Fi: sigue aunque bloquees el iPhone
/// o salgas de Ari, y no gasta datos móviles.
final class ModelDownloader: NSObject, URLSessionDownloadDelegate {
    enum DownloadError: LocalizedError {
        case http(Int)

        var errorDescription: String? {
            switch self {
            case .http(401), .http(403):
                return "La página del modelo pidió iniciar sesión. Descárgalo en el navegador y elígelo con \"Usar un archivo\"."
            case .http(let code):
                return "La descarga falló (código \(code)). Revisa tu Wi-Fi e inténtalo de nuevo."
            }
        }
    }

    static let sessionIdentifier = "com.ari.descarga-modelo"

    /// Se llaman desde un hilo de fondo.
    var onProgress: ((AriModel, Double) -> Void)?
    var onFinish: ((AriModel, Result<URL, Error>) -> Void)?

    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.background(withIdentifier: ModelDownloader.sessionIdentifier)
        config.allowsCellularAccess = false
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()

    /// Al abrir la app, retoma una descarga que haya seguido mientras estaba cerrada.
    func reconnect() {
        _ = session
    }

    func start(_ model: AriModel) {
        let task = session.downloadTask(with: model.url)
        task.taskDescription = model.rawValue
        task.resume()
    }

    func cancelAll() {
        session.getAllTasks { tasks in tasks.forEach { $0.cancel() } }
    }

    func activeModel() async -> AriModel? {
        let tasks = await session.allTasks
        return tasks.first { $0.state == .running || $0.state == .suspended }
            .flatMap { AriModel(rawValue: $0.taskDescription ?? "") }
    }

    // MARK: URLSessionDownloadDelegate

    func urlSession(
        _ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64,
        totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64
    ) {
        guard let model = AriModel(rawValue: downloadTask.taskDescription ?? "") else { return }
        let total = totalBytesExpectedToWrite > 0 ? totalBytesExpectedToWrite : model.bytes
        onProgress?(model, min(1, Double(totalBytesWritten) / Double(total)))
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        guard let model = AriModel(rawValue: downloadTask.taskDescription ?? "") else { return }
        if let http = downloadTask.response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            onFinish?(model, .failure(DownloadError.http(http.statusCode)))
            return
        }
        // El archivo temporal se borra al salir de este método: hay que moverlo ya.
        do {
            let installed = try ModelFiles.install(location, as: model, copy: false)
            onFinish?(model, .success(installed))
        } catch {
            onFinish?(model, .failure(error))
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard let error, let model = AriModel(rawValue: task.taskDescription ?? "") else { return }
        if (error as NSError).code == NSURLErrorCancelled { return }
        onFinish?(model, .failure(error))
    }
}
