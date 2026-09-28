import SwiftUI

/// "Your notes" on a company page (Sprint 9): the user's private notes about this company, newest first,
/// with add / edit / delete. Placed by both layouts just above the disclaimer; the same component either
/// way. Notes are words, never numbers, so this reads nothing from the report beyond the company.
struct CompanyNotesSection: View {
    let company: CompanyRef
    /// Optional: rendered without a store (layout tests, the Settings previews) the section is omitted.
    @Environment(NotesStore.self) private var store: NotesStore?
    @State private var editing: NoteEditorTarget?

    var body: some View {
        if let store { section(store) }
    }

    private func section(_ store: NotesStore) -> some View {
        let notes = store.notes(for: company.ticker)
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Your notes").font(.vlHeadline).foregroundStyle(Theme.textPrimary)
                    Text("Private — kept on this device only.").font(.caption).foregroundStyle(Theme.textTertiary)
                }
                Spacer()
                Button { editing = .new } label: { Label("Add", systemImage: "square.and.pencil").font(.callout.weight(.medium)) }
                    .accessibilityLabel("Add a note about \(company.ticker)")
            }
            if notes.isEmpty {
                Button { editing = .new } label: {
                    Text("Write down why you're watching \(company.ticker), what would change your mind, or anything to check next quarter.")
                        .font(.caption).foregroundStyle(Theme.textSecondary)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Theme.surfaceRaised, in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            } else {
                ForEach(notes) { note in
                    Button { editing = .existing(note) } label: { NoteRow(note: note) }
                        .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(Theme.border))
        .sheet(item: $editing) { target in
            NoteEditorSheet(company: company, target: target)
        }
    }
}

private struct NoteRow: View {
    let note: CompanyNote
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(note.text).font(.callout).foregroundStyle(Theme.textPrimary)
                .lineLimit(4).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(NoteDates.caption(note)).font(.caption2).foregroundStyle(Theme.textTertiary)
        }
        .padding(12)
        .background(Theme.surfaceRaised, in: RoundedRectangle(cornerRadius: 10))
        .contentShape(Rectangle())
        .accessibilityHint("Opens the note to edit")
    }
}

enum NoteDates {
    /// "Sep 28, 2026" or "Written Sep 20, 2026 · edited Sep 28, 2026".
    static func caption(_ n: CompanyNote) -> String {
        let written = n.createdAt.formatted(date: .abbreviated, time: .omitted)
        let edited = n.updatedAt.formatted(date: .abbreviated, time: .omitted)
        return written == edited ? written : "Written \(written) · edited \(edited)"
    }
}

enum NoteEditorTarget: Identifiable {
    case new
    case existing(CompanyNote)
    var id: String { if case .existing(let n) = self { return n.id.uuidString }; return "new" }
}

struct NoteEditorSheet: View {
    let company: CompanyRef
    let target: NoteEditorTarget
    @Environment(NotesStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var confirmDelete = false
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            TextEditor(text: $text)
                .focused($focused)
                .scrollContentBackground(.hidden)
                .padding(12)
                .background(Theme.background)
                .navigationTitle(company.ticker)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button("Save") { save() }.disabled(!canSave) }
                    if case .existing = target {
                        ToolbarItem(placement: .bottomBar) {
                            Button(role: .destructive) { confirmDelete = true } label: { Label("Delete note", systemImage: "trash") }
                        }
                    }
                }
                .confirmationDialog("Delete this note?", isPresented: $confirmDelete, titleVisibility: .visible) {
                    Button("Delete", role: .destructive) { if case .existing(let n) = target { store.delete(id: n.id) }; dismiss() }
                } message: { Text("It is stored only on this device, so it can't be recovered.") }
        }
        .tint(Theme.accent)
        .onAppear {
            if case .existing(let n) = target { text = n.text }
            focused = true
        }
    }

    private var canSave: Bool {
        switch target {
        case .new: return !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .existing: return true   // an emptied note is deleted on save, as in Notes
        }
    }

    private func save() {
        switch target {
        case .new: store.add(ticker: company.ticker, companyName: company.displayName, text: text)
        case .existing(let n): store.update(id: n.id, text: text)
        }
        dismiss()
    }
}
