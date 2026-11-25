import Foundation
import ImageIO
import UIKit

struct PhotoMeta {
    var latitude: Double?
    var longitude: Double?
    var altitude: Double?
    var date: Date = Date()
    var dateFromExif: Bool = false
    var make: String = ""
    var model: String = ""
    var lens: String = ""
    var width: Int = 0
    var height: Int = 0
    var orientation: Int = 1

    var deviceText: String {
        let body: String
        if model.isBlank {
            body = make
        } else if make.isBlank || model.lowercased().hasPrefix(make.lowercased()) {
            body = model
        } else {
            body = "\(make) \(model)"
        }
        if lens.isBlank { return body }
        return body.isBlank ? lens : "\(body) · \(lens)"
    }

    var hasGPS: Bool {
        latitude != nil && longitude != nil
    }
}

enum PhotoLoader {
    static func meta(from data: Data) -> PhotoMeta {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else {
            return PhotoMeta()
        }
        let props = CGImageSourceCopyPropertiesAtIndex(
            source, 0, [kCGImageSourceShouldCache: false] as CFDictionary
        ) as? [String: Any] ?? [:]
        let gps = props[kCGImagePropertyGPSDictionary as String] as? [String: Any] ?? [:]
        let exif = props[kCGImagePropertyExifDictionary as String] as? [String: Any] ?? [:]
        let tiff = props[kCGImagePropertyTIFFDictionary as String] as? [String: Any] ?? [:]

        var latitude: Double?
        var longitude: Double?
        if let rawLat = number(gps[kCGImagePropertyGPSLatitude as String]),
           let rawLon = number(gps[kCGImagePropertyGPSLongitude as String]) {
            let latRef = (gps[kCGImagePropertyGPSLatitudeRef as String] as? String) ?? "N"
            let lonRef = (gps[kCGImagePropertyGPSLongitudeRef as String] as? String) ?? "E"
            let lat = latRef.uppercased() == "S" ? -rawLat : rawLat
            let lon = lonRef.uppercased() == "W" ? -rawLon : rawLon
            if !(lat == 0 && lon == 0) {
                latitude = lat
                longitude = lon
            }
        }

        var altitude: Double?
        if let rawAltitude = number(gps[kCGImagePropertyGPSAltitude as String]) {
            let below = (gps[kCGImagePropertyGPSAltitudeRef as String] as? NSNumber)?.intValue == 1
            altitude = below ? -rawAltitude : rawAltitude
        }

        let dateText = (exif[kCGImagePropertyExifDateTimeOriginal as String] as? String)
            ?? (exif[kCGImagePropertyExifDateTimeDigitized as String] as? String)
            ?? (tiff[kCGImagePropertyTIFFDateTime as String] as? String)
        let parsedDate = dateText.flatMap { exifDateFormatter.date(from: $0.trimmed) }

        let width = intValue(props[kCGImagePropertyPixelWidth as String])
            ?? intValue(exif[kCGImagePropertyExifPixelXDimension as String]) ?? 0
        let height = intValue(props[kCGImagePropertyPixelHeight as String])
            ?? intValue(exif[kCGImagePropertyExifPixelYDimension as String]) ?? 0

        return PhotoMeta(
            latitude: latitude,
            longitude: longitude,
            altitude: altitude,
            date: parsedDate ?? Date(),
            dateFromExif: parsedDate != nil,
            make: ((tiff[kCGImagePropertyTIFFMake as String] as? String) ?? "").trimmed,
            model: ((tiff[kCGImagePropertyTIFFModel as String] as? String) ?? "").trimmed,
            lens: ((exif[kCGImagePropertyExifLensModel as String] as? String) ?? "").trimmed,
            width: width,
            height: height,
            orientation: intValue(props[kCGImagePropertyOrientation as String]) ?? 1
        )
    }

    static func thumbnail(data: Data, maxPixel: Int) -> UIImage? {
        image(data: data, maxPixel: maxPixel)
    }

    static func fullImage(data: Data, maxSide: Int) -> UIImage? {
        image(data: data, maxPixel: max(1024, maxSide))
    }

    static func originalProperties(data: Data) -> [String: Any]? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        return CGImageSourceCopyPropertiesAtIndex(
            source, 0, [kCGImageSourceShouldCache: false] as CFDictionary
        ) as? [String: Any]
    }

    private static func image(data: Data, maxPixel: Int) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        return UIImage(cgImage: cgImage, scale: 1, orientation: .up)
    }

    private static func number(_ value: Any?) -> Double? {
        if let number = value as? NSNumber { return number.doubleValue }
        if let text = value as? String { return Double(text) }
        return nil
    }

    private static func intValue(_ value: Any?) -> Int? {
        (value as? NSNumber)?.intValue
    }

    private static var exifDateFormatter: DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone.current
        f.dateFormat = "yyyy:MM:dd HH:mm:ss"
        return f
    }
}
