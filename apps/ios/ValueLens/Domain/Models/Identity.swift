import Foundation

/// The SEC EDGAR fair-access identity: "Full Name email@domain" forwarded on every EDGAR request.
struct SECIdentity: Codable, Equatable {
    var fullName: String
    var email: String

    var userAgent: String { "\(fullName.trimmingCharacters(in: .whitespaces)) \(email.trimmingCharacters(in: .whitespaces))" }

    var isValid: Bool {
        let name = fullName.trimmingCharacters(in: .whitespaces)
        let mail = email.trimmingCharacters(in: .whitespaces)
        return name.split(separator: " ").count >= 2 && mail.contains("@") && mail.contains(".")
    }
}
