import Foundation

/// HTTP client for the valuation engine. Forwards the user's SEC identity as `X-SEC-User-Agent`.
struct APIClient: Sendable {
    let baseURL: URL
    let userAgent: String?

    func get<T: Decodable>(_ path: String, query: [URLQueryItem] = []) async throws -> T {
        var comps = URLComponents(url: baseURL.appending(path: path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { comps.queryItems = query }
        var req = URLRequest(url: comps.url!)
        req.timeoutInterval = 60
        if let userAgent { req.setValue(userAgent, forHTTPHeaderField: "X-SEC-User-Agent") }
        let (data, resp): (Data, URLResponse)
        do {
            (data, resp) = try await URLSession.shared.data(for: req)
        } catch {
            throw RepositoryError.offline
        }
        guard let http = resp as? HTTPURLResponse else { throw RepositoryError.offline }
        guard (200..<300).contains(http.statusCode) else {
            throw RepositoryError.from(status: http.statusCode, body: data)
        }
        do {
            return try JSONDecoder.engine.decode(T.self, from: data)
        } catch {
            throw RepositoryError.decoding(String(describing: error))
        }
    }
}
