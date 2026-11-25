import Foundation
import ImageIO
import Photos
import UIKit
import UniformTypeIdentifiers

struct ExportPayload: Identifiable, @unchecked Sendable {
    let id = UUID()
    let data: Data
    let image: UIImage
    let fileName: String
}

enum PhotoExportError: LocalizedError {
    case decodeFailed
    case renderFailed
    case encodeFailed
    case permissionDenied
    case saveFailed

    var errorDescription: String? {
        switch self {
        case .decodeFailed: return "无法解码这张照片"
        case .renderFailed: return "水印渲染失败"
        case .encodeFailed: return "JPEG 编码失败"
        case .permissionDenied: return "没有相册写入权限，请在系统设置中允许"
        case .saveFailed: return "保存到相册失败"
        }
    }
}

enum PhotoExporter {
    static func render(
        data: Data,
        template: WatermarkTemplate,
        text: FormattedWatermark,
        transform: WatermarkTransform,
        maxSide: Int,
        quality: CGFloat,
        keepExif: Bool,
        comment: String
    ) throws -> ExportPayload {
        guard let image = PhotoLoader.fullImage(data: data, maxSide: maxSide) else {
            throw PhotoExportError.decodeFailed
        }

        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        if #available(iOS 12.0, *) {
            format.preferredRange = .standard
        }
        let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
        let output = renderer.image { context in
            image.draw(in: CGRect(origin: .zero, size: image.size))
            WatermarkRenderer.draw(
                in: context.cgContext,
                canvasSize: image.size,
                template: template,
                text: text,
                transform: transform
            )
        }
        guard let cgImage = output.cgImage else { throw PhotoExportError.renderFailed }

        let original = keepExif ? PhotoLoader.originalProperties(data: data) : nil
        let props = metadata(
            original: original,
            pixelSize: CGSize(width: cgImage.width, height: cgImage.height),
            keepExif: keepExif,
            comment: comment,
            quality: quality
        )
        let buffer = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            buffer, UTType.jpeg.identifier as CFString, 1, nil
        ) else { throw PhotoExportError.encodeFailed }
        CGImageDestinationAddImage(destination, cgImage, props as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { throw PhotoExportError.encodeFailed }

        let stamp = fileDateFormatter.string(from: Date())
        return ExportPayload(
            data: buffer as Data,
            image: output,
            fileName: "TraceMark_\(stamp).jpg"
        )
    }

    private static func metadata(
        original: [String: Any]?,
        pixelSize: CGSize,
        keepExif: Bool,
        comment: String,
        quality: CGFloat
    ) -> [String: Any] {
        var props: [String: Any] = keepExif ? (original ?? [:]) : [:]
        props[kCGImagePropertyOrientation as String] = 1
        props[kCGImagePropertyPixelWidth as String] = Int(pixelSize.width)
        props[kCGImagePropertyPixelHeight as String] = Int(pixelSize.height)

        var exif = props[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        exif[kCGImagePropertyExifPixelXDimension as String] = Int(pixelSize.width)
        exif[kCGImagePropertyExifPixelYDimension as String] = Int(pixelSize.height)
        exif[kCGImagePropertyExifUserComment as String] = comment
        props[kCGImagePropertyExifDictionary as String] = exif

        var tiff = props[kCGImagePropertyTIFFDictionary as String] as? [String: Any] ?? [:]
        tiff[kCGImagePropertyTIFFSoftware as String] = "TraceMark 1.1 (iOS)"
        tiff[kCGImagePropertyTIFFImageDescription as String] = comment
        props[kCGImagePropertyTIFFDictionary as String] = tiff

        props[kCGImageDestinationLossyCompressionQuality as String] = quality
        return props
    }

    private static var fileDateFormatter: DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMdd_HHmmss"
        return f
    }
}

enum PhotoSaver {
    static func saveToAlbum(data: Data, fileName: String) async throws {
        let status = await requestAddAuthorization()
        guard status == .authorized || status == .limited else {
            throw PhotoExportError.permissionDenied
        }
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                let options = PHAssetResourceCreationOptions()
                options.originalFilename = fileName
                request.addResource(with: .photo, data: data, options: options)
            } completionHandler: { success, error in
                if success {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: error ?? PhotoExportError.saveFailed)
                }
            }
        }
    }

    static func writeTemporaryFile(data: Data, fileName: String) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
        try data.write(to: url, options: .atomic)
        return url
    }

    private static func requestAddAuthorization() async -> PHAuthorizationStatus {
        await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                continuation.resume(returning: status)
            }
        }
    }
}
