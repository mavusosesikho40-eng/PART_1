import SwiftUI
import UIKit
import ComposeApp

/// Shows the Compose app (MainViewController.kt) full screen.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose handles the safe areas and the keyboard itself.
        ComposeView()
            .ignoresSafeArea()
    }
}
