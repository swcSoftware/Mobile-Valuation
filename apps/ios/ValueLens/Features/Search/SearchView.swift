import SwiftUI

struct SearchView: View {
    @Environment(AppSettings.self) private var settings
    @Environment(AppRouter.self) private var router
    @State private var query = ""
    @State private var results: [CompanyRef] = []
    @State private var searching = false
    @State private var error: String?
    @State private var task: Task<Void, Never>?

    var body: some View {
        @Bindable var router = router
        NavigationStack(path: $router.searchPath) {
            List {
                if results.isEmpty && !query.isEmpty && !searching {
                    Text(error ?? "No matches in SEC's company list.").foregroundStyle(Theme.textSecondary)
                        .listRowBackground(Theme.surface)
                }
                ForEach(results) { c in
                    NavigationLink(value: c) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(c.ticker).font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                            Text(c.name).font(.caption).foregroundStyle(Theme.textSecondary)
                        }
                    }
                    .listRowBackground(Theme.surface)
                }
                if query.isEmpty {
                    Section("Try") {
                        ForEach(["AAPL", "KO", "MSFT", "JNJ", "PG", "BRK-B"], id: \.self) { t in
                            Button(t) { query = t }.foregroundStyle(Theme.value)
                        }
                    }
                    .listRowBackground(Theme.surface)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(Theme.background)
            .navigationTitle("Search")
            .searchable(text: $query, prompt: "Ticker or company name")
            .textInputAutocapitalization(.characters)
            .autocorrectionDisabled()
            .overlay { if searching { ProgressView() } }
            .navigationDestination(for: CompanyRef.self) { CompanyDetailView(company: $0) }
            .onChange(of: query) { _, q in
                task?.cancel()
                guard !q.isEmpty else { results = []; return }
                task = Task {
                    try? await Task.sleep(for: .milliseconds(300))
                    guard !Task.isCancelled else { return }
                    searching = true; defer { searching = false }
                    do {
                        results = try await settings.repository.search(query: q)
                        error = nil
                    } catch {
                        results = []; self.error = error.localizedDescription
                    }
                }
            }
        }
    }
}
