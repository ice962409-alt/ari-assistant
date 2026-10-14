// swift-tools-version: 5.9
// La lógica de Ari que no depende de la pantalla ni del modelo: perfil y personalidad,
// comandos de memoria, historial para Gemma, elección de modelo, guardado e informes.
// Se prueba con `swift test` sin necesidad de un iPhone.

import PackageDescription

let package = Package(
    name: "AriCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "AriCore", targets: ["AriCore"]),
    ],
    targets: [
        .target(name: "AriCore"),
        .testTarget(name: "AriCoreTests", dependencies: ["AriCore"]),
    ]
)
