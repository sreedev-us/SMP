# main.py
import tkinter as tk
from music_player import MusicPlayer
from auth import Authentication, LoginWindow
from settings import SettingsManager

class MainApplication:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("Music Player")
        self.root.geometry("1100x750")
        self.root.configure(bg='#1e1e1e')
        
        # Initialize systems
        self.auth = Authentication()
        self.settings = SettingsManager()
        
        # Show login screen first
        self.show_login_screen()
    
    def show_login_screen(self):
        """Show login/registration screen"""
        LoginWindow(self.root, self.auth, self.on_login_success)
    
    def on_login_success(self, username):
        """Callback when user successfully logs in"""
        print(f"User {username} logged in successfully!")
        self.show_main_interface()
    
    def show_main_interface(self):
        """Show the main music player interface"""
        # Destroy any existing widgets
        for widget in self.root.winfo_children():
            widget.destroy()
        
        # Create main music player
        self.music_player = MusicPlayer(self.root, self.auth, self.settings)
        
        # Add settings button to main interface
        self.add_settings_button()
    
    def add_settings_button(self):
        """Add settings button to the main interface"""
        settings_btn = tk.Button(
            self.root,
            text="⚙️",
            command=self.open_settings,
            font=('Arial', 14),
            bg='#007acc',
            fg='white',
            relief='flat',
            width=3
        )
        settings_btn.place(relx=0.95, rely=0.02, anchor=tk.NE)
    
    def open_settings(self):
        """Open settings window"""
        from settings import SettingsWindow
        SettingsWindow(self.root, self.settings, self.auth)
    
    def run(self):
        """Start the application"""
        self.root.mainloop()

if __name__ == "__main__":
    app = MainApplication()
    app.run()