import AVFoundation
import Foundation

/// "Always On": keeps the app running in the background by playing silence (mixed with other
/// audio, so music and calls aren't affected). While it runs, the app keeps the alert queue full.
@MainActor
final class KeepAlive {
    private var player: AVAudioPlayer?
    private var observers: [NSObjectProtocol] = []

    var isRunning: Bool { player?.isPlaying == true }

    func start() {
        if let player {
            if !player.isPlaying { player.play() }
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try session.setActive(true)
            let p = try AVAudioPlayer(contentsOf: try Self.silentFile())
            p.numberOfLoops = -1
            p.volume = 0
            p.prepareToPlay()
            p.play()
            player = p
            observe()
        } catch {
            player = nil
        }
    }

    func stop() {
        player?.stop()
        player = nil
        observers.forEach { NotificationCenter.default.removeObserver($0) }
        observers = []
    }

    private func observe() {
        guard observers.isEmpty else { return }
        let center = NotificationCenter.default
        // A call or Siri pauses us; pick up again when it's over.
        observers.append(center.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] note in
            guard
                let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                AVAudioSession.InterruptionType(rawValue: raw) == .ended
            else { return }
            MainActor.assumeIsolated { self?.resume() }
        })
        observers.append(center.addObserver(
            forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.player = nil
                self?.start()
            }
        })
    }

    private func resume() {
        try? AVAudioSession.sharedInstance().setActive(true)
        player?.play()
    }

    /// One second of 16-bit mono silence as a WAV file.
    private static func silentFile() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("silence.wav")
        if FileManager.default.fileExists(atPath: url.path) { return url }
        let sampleRate: UInt32 = 22_050
        let dataSize: UInt32 = sampleRate * 2
        var data = Data()
        func append<T: FixedWidthInteger>(_ value: T) {
            var little = value.littleEndian
            withUnsafeBytes(of: &little) { data.append(contentsOf: $0) }
        }
        data.append(contentsOf: Array("RIFF".utf8))
        append(UInt32(36) + dataSize)
        data.append(contentsOf: Array("WAVEfmt ".utf8))
        append(UInt32(16)) // fmt chunk size
        append(UInt16(1)) // PCM
        append(UInt16(1)) // mono
        append(sampleRate)
        append(sampleRate * 2) // byte rate
        append(UInt16(2)) // block align
        append(UInt16(16)) // bits per sample
        data.append(contentsOf: Array("data".utf8))
        append(dataSize)
        data.append(Data(count: Int(dataSize)))
        try data.write(to: url, options: .atomic)
        return url
    }
}
