import AVFoundation
import Foundation

struct ImportedSound: Sendable {
    let fileName: String
    let displayName: String
    let seconds: Double
}

enum SoundError: LocalizedError {
    case unreadable, tooManyChannels, empty

    var errorDescription: String? {
        switch self {
        case .unreadable: return "That file couldn't be read as audio. Try an MP3, M4A, WAV, AIFF or CAF file."
        case .tooManyChannels: return "Surround-sound files aren't supported. Use a mono or stereo file."
        case .empty: return "That audio file is empty."
        }
    }
}

/// Custom alert sounds. iOS only plays notification sounds that live in Library/Sounds,
/// are 30 seconds or shorter and use an uncompressed format, so imports are converted to CAF.
enum SoundLibrary {
    static var folder: URL {
        FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Sounds", isDirectory: true)
    }

    static func url(for file: String) -> URL { folder.appendingPathComponent(file) }

    static func exists(_ file: String) -> Bool {
        FileManager.default.fileExists(atPath: url(for: file).path)
    }

    static func delete(_ file: String) {
        try? FileManager.default.removeItem(at: url(for: file))
    }

    /// Converts the picked file into Library/Sounds/order-<time>.caf (cut to 29 seconds).
    static func importSound(from source: URL) throws -> ImportedSound {
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let name = "order-\(Int(Date().timeIntervalSince1970)).caf"
        let destination = url(for: name)
        do {
            let seconds = try convert(source, to: destination)
            return ImportedSound(
                fileName: name,
                displayName: source.deletingPathExtension().lastPathComponent,
                seconds: seconds
            )
        } catch {
            try? FileManager.default.removeItem(at: destination)
            throw error
        }
    }

    private static func convert(_ source: URL, to destination: URL) throws -> Double {
        let input: AVAudioFile
        do {
            input = try AVAudioFile(forReading: source)
        } catch {
            throw SoundError.unreadable
        }
        let format = input.processingFormat
        guard format.channelCount <= 2 else { throw SoundError.tooManyChannels }
        let limit = min(input.length, AVAudioFramePosition(29.0 * format.sampleRate))
        guard limit > 0 else { throw SoundError.empty }

        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatLinearPCM,
            AVSampleRateKey: format.sampleRate,
            AVNumberOfChannelsKey: format.channelCount,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false,
        ]
        let output = try AVAudioFile(
            forWriting: destination, settings: settings,
            commonFormat: format.commonFormat, interleaved: format.isInterleaved
        )
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: 8192) else {
            throw SoundError.unreadable
        }
        var written: AVAudioFramePosition = 0
        while written < limit {
            let want = AVAudioFrameCount(min(AVAudioFramePosition(buffer.frameCapacity), limit - written))
            try input.read(into: buffer, frameCount: want)
            if buffer.frameLength == 0 { break }
            try output.write(from: buffer)
            written += AVAudioFramePosition(buffer.frameLength)
        }
        guard written > 0 else { throw SoundError.empty }
        return Double(written) / format.sampleRate
    }
}
