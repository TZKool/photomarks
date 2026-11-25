import Foundation

/// WGS84 / GCJ-02 / BD-09 conversions for mainland China.
enum CoordTransform {
    private static let a = 6378245.0
    private static let ee = 0.00669342162296594323
    private static let xPi = Double.pi * 3000.0 / 180.0

    static func outOfChina(lat: Double, lon: Double) -> Bool {
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271
    }

    static func wgs84ToGcj02(lat: Double, lon: Double) -> (lat: Double, lon: Double) {
        if outOfChina(lat: lat, lon: lon) { return (lat, lon) }
        var dLat = transformLat(x: lon - 105.0, y: lat - 35.0)
        var dLon = transformLon(x: lon - 105.0, y: lat - 35.0)
        let radLat = lat / 180.0 * Double.pi
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        let sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Double.pi)
        dLon = (dLon * 180.0) / (a / sqrtMagic * cos(radLat) * Double.pi)
        return (lat + dLat, lon + dLon)
    }

    static func gcj02ToWgs84(lat: Double, lon: Double) -> (lat: Double, lon: Double) {
        if outOfChina(lat: lat, lon: lon) { return (lat, lon) }
        var wgsLat = lat
        var wgsLon = lon
        for _ in 0..<4 {
            let gcj = wgs84ToGcj02(lat: wgsLat, lon: wgsLon)
            wgsLat += lat - gcj.lat
            wgsLon += lon - gcj.lon
        }
        return (wgsLat, wgsLon)
    }

    static func gcj02ToBd09(lat: Double, lon: Double) -> (lat: Double, lon: Double) {
        let z = sqrt(lon * lon + lat * lat) + 0.00002 * sin(lat * xPi)
        let theta = atan2(lat, lon) + 0.000003 * cos(lon * xPi)
        return (z * sin(theta) + 0.006, z * cos(theta) + 0.0065)
    }

    static func bd09ToGcj02(lat: Double, lon: Double) -> (lat: Double, lon: Double) {
        let x = lon - 0.0065
        let y = lat - 0.006
        let z = sqrt(x * x + y * y) - 0.00002 * sin(y * xPi)
        let theta = atan2(y, x) - 0.000003 * cos(x * xPi)
        return (z * sin(theta), z * cos(theta))
    }

    static func wgs84ToBd09(lat: Double, lon: Double) -> (lat: Double, lon: Double) {
        let gcj = wgs84ToGcj02(lat: lat, lon: lon)
        return gcj02ToBd09(lat: gcj.lat, lon: gcj.lon)
    }

    static func convert(lat: Double, lon: Double, to system: CoordinateSystem) -> (lat: Double, lon: Double) {
        switch system {
        case .wgs84: return (lat, lon)
        case .gcj02: return wgs84ToGcj02(lat: lat, lon: lon)
        case .bd09: return wgs84ToBd09(lat: lat, lon: lon)
        }
    }

    private static func transformLat(x: Double, y: Double) -> Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Double.pi) + 20.0 * sin(2.0 * x * Double.pi)) * 2.0 / 3.0
        ret += (20.0 * sin(y * Double.pi) + 40.0 * sin(y / 3.0 * Double.pi)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * Double.pi) + 320 * sin(y * Double.pi / 30.0)) * 2.0 / 3.0
        return ret
    }

    private static func transformLon(x: Double, y: Double) -> Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * Double.pi) + 20.0 * sin(2.0 * x * Double.pi)) * 2.0 / 3.0
        ret += (20.0 * sin(x * Double.pi) + 40.0 * sin(x / 3.0 * Double.pi)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * Double.pi) + 300.0 * sin(x / 30.0 * Double.pi)) * 2.0 / 3.0
        return ret
    }
}

enum GeoMath {
    static func distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let r = 6371000.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }
}
