import Foundation
import AriCore

/// Dónde vive el modelo en el iPhone: en el almacenamiento privado de Ari, fuera del respaldo
/// de iCloud (pesa varios GB y se puede volver a descargar).
enum ModelFiles {
    static var directory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Modelos", isDirectory: true)
    }

    static var cacheDir: URL {
        let url = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("LiteRTLM", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    static func url(for model: AriModel) -> URL {
        directory.appendingPathComponent(model.fileName)
    }

    static func exists(_ model: AriModel) -> Bool {
        let file = url(for: model)
        let attributes = try? FileManager.default.attributesOfItem(atPath: file.path)
        let size = (attributes?[.size] as? NSNumber)?.int64Value ?? 0
        return size > 0
    }

    /// Mueve un archivo a su lugar definitivo y lo excluye del respaldo.
    static func install(_ source: URL, as model: AriModel, copy: Bool) throws -> URL {
        let fm = FileManager.default
        try fm.createDirectory(at: directory, withIntermediateDirectories: true)
        var destination = url(for: model)
        if fm.fileExists(atPath: destination.path) { try fm.removeItem(at: destination) }
        if copy {
            try fm.copyItem(at: source, to: destination)
        } else {
            try fm.moveItem(at: source, to: destination)
        }
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try destination.setResourceValues(values)
        return destination
    }

    static func delete(_ model: AriModel) {
        try? FileManager.default.removeItem(at: url(for: model))
    }

    /// Espacio libre que iOS considera disponible para cosas importantes.
    static var freeBytes: Int64 {
        let home = URL(fileURLWithPath: NSHomeDirectory())
        let values = try? home.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        return values?.volumeAvailableCapacityForImportantUsage ?? 0
    }
}
