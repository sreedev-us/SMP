# settings.py
import tkinter as tk
from tkinter import ttk, messagebox
import json
from pathlib import Path

class SettingsManager:
    def __init__(self, config_file="config.json"):
        self.config_file = Path(config_file)
        self.settings = self.load_default_settings()
        self.load_settings()
    
    def load_default_settings(self):
        """Return default settings"""
        return {
            'audio': {
                'volume': 70,
                'autoplay': True,
                'crossfade': False,
                'crossfade_duration': 3
            },
            'interface': {
                'theme': 'dark',
                'font_size': 11,
                'show_album_art': True,
                'language': 'english'
            },
            'youtube': {
                'api_key': '',
                'max_search_results': 10,
                'audio_quality': 'best',
                'auto_add_to_playlist': False
            },
            'playback': {
                'repeat_mode': 'none',  # none, one, all
                'shuffle': False,
                'resume_playback': True
            },
            'storage': {
                'cache_size': 100,  # MB
                'auto_clear_cache': True,
                'save_playlists': True
            }
        }
    
    def load_settings(self):
        """Load settings from file"""
        if self.config_file.exists():
            try:
                with open(self.config_file, 'r') as f:
                    loaded_settings = json.load(f)
                    # Merge with default settings
                    self.merge_settings(loaded_settings)
            except Exception as e:
                print(f"Error loading settings: {e}")
    
    def merge_settings(self, new_settings):
        """Recursively merge settings"""
        for key, value in new_settings.items():
            if key in self.settings and isinstance(value, dict) and isinstance(self.settings[key], dict):
                self.merge_settings_dict(self.settings[key], value)
            else:
                self.settings[key] = value
    
    def merge_settings_dict(self, target, source):
        """Merge two dictionaries recursively"""
        for key, value in source.items():
            if key in target and isinstance(value, dict) and isinstance(target[key], dict):
                self.merge_settings_dict(target[key], value)
            else:
                target[key] = value
    
    def save_settings(self):
        """Save settings to file"""
        try:
            with open(self.config_file, 'w') as f:
                json.dump(self.settings, f, indent=2)
            return True
        except Exception as e:
            print(f"Error saving settings: {e}")
            return False
    
    def get(self, key_path, default=None):
        """Get setting by key path (e.g., 'audio.volume')"""
        keys = key_path.split('.')
        value = self.settings
        try:
            for key in keys:
                value = value[key]
            return value
        except (KeyError, TypeError):
            return default
    
    def set(self, key_path, value):
        """Set setting by key path"""
        keys = key_path.split('.')
        settings_ref = self.settings
        
        # Navigate to the parent of the final key
        for key in keys[:-1]:
            if key not in settings_ref:
                settings_ref[key] = {}
            settings_ref = settings_ref[key]
        
        # Set the final key
        settings_ref[keys[-1]] = value

class SettingsWindow:
    def __init__(self, parent, settings_manager, auth_system):
        self.parent = parent
        self.settings = settings_manager
        self.auth = auth_system
        
        self.window = tk.Toplevel(parent)
        self.window.title("Settings - Harmony Music Player")
        self.window.geometry("600x700")
        self.window.configure(bg='#1e1e1e')
        
        self.create_settings_ui()
    
    def create_settings_ui(self):
        # Main container with notebook (tabs)
        main_frame = tk.Frame(self.window, bg='#1e1e1e')
        main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        # Create notebook (tabbed interface)
        self.notebook = ttk.Notebook(main_frame)
        self.notebook.pack(fill=tk.BOTH, expand=True)
        
        # Apply custom style to notebook
        style = ttk.Style()
        style.configure('TNotebook', background='#1e1e1e')
        style.configure('TNotebook.Tab', background='#2d2d2d', foreground='white')
        
        # Create tabs
        self.audio_tab = self.create_audio_tab()
        self.interface_tab = self.create_interface_tab()
        self.youtube_tab = self.create_youtube_tab()
        self.playback_tab = self.create_playback_tab()
        self.account_tab = self.create_account_tab()
        
        self.notebook.add(self.audio_tab, text="Audio")
        self.notebook.add(self.interface_tab, text="Interface")
        self.notebook.add(self.youtube_tab, text="YouTube")
        self.notebook.add(self.playback_tab, text="Playback")
        self.notebook.add(self.account_tab, text="Account")
        
        # Buttons frame
        buttons_frame = tk.Frame(main_frame, bg='#1e1e1e')
        buttons_frame.pack(fill=tk.X, pady=(10, 0))
        
        save_btn = tk.Button(
            buttons_frame,
            text="Save Settings",
            command=self.save_all_settings,
            font=('Arial', 10, 'bold'),
            bg='#388e3c',
            fg='white',
            relief='flat',
            padx=20
        )
        save_btn.pack(side=tk.RIGHT, padx=(10, 0))
        
        cancel_btn = tk.Button(
            buttons_frame,
            text="Cancel",
            command=self.window.destroy,
            font=('Arial', 10),
            bg='#757575',
            fg='white',
            relief='flat',
            padx=20
        )
        cancel_btn.pack(side=tk.RIGHT)
        
        reset_btn = tk.Button(
            buttons_frame,
            text="Reset to Defaults",
            command=self.reset_settings,
            font=('Arial', 10),
            bg='#ff9800',
            fg='white',
            relief='flat',
            padx=20
        )
        reset_btn.pack(side=tk.LEFT)
    
    def create_audio_tab(self):
        frame = tk.Frame(self.notebook, bg='#1e1e1e')
        
        # Volume
        tk.Label(
            frame,
            text="Default Volume:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.volume_var = tk.IntVar(value=self.settings.get('audio.volume', 70))
        volume_scale = ttk.Scale(
            frame,
            from_=0,
            to=100,
            variable=self.volume_var,
            orient=tk.HORIZONTAL
        )
        volume_scale.pack(fill=tk.X, pady=(0, 15))
        
        # Auto-play
        self.autoplay_var = tk.BooleanVar(value=self.settings.get('audio.autoplay', True))
        autoplay_cb = tk.Checkbutton(
            frame,
            text="Auto-play next song",
            variable=self.autoplay_var,
            font=('Arial', 10),
            fg='#ffffff',
            bg='#1e1e1e',
            selectcolor='#2d2d2d',
            activebackground='#1e1e1e',
            activeforeground='#ffffff'
        )
        autoplay_cb.pack(anchor=tk.W, pady=5)
        
        # Crossfade
        self.crossfade_var = tk.BooleanVar(value=self.settings.get('audio.crossfade', False))
        crossfade_cb = tk.Checkbutton(
            frame,
            text="Enable crossfade between songs",
            variable=self.crossfade_var,
            font=('Arial', 10),
            fg='#ffffff',
            bg='#1e1e1e',
            selectcolor='#2d2d2d'
        )
        crossfade_cb.pack(anchor=tk.W, pady=5)
        
        # Crossfade duration (only enabled if crossfade is checked)
        crossfade_frame = tk.Frame(frame, bg='#1e1e1e')
        crossfade_frame.pack(fill=tk.X, pady=(5, 15))
        
        tk.Label(
            crossfade_frame,
            text="Crossfade Duration (seconds):",
            font=('Arial', 9),
            fg='#cccccc',
            bg='#1e1e1e'
        ).pack(anchor=tk.W)
        
        self.crossfade_duration_var = tk.IntVar(value=self.settings.get('audio.crossfade_duration', 3))
        crossfade_spinbox = tk.Spinbox(
            crossfade_frame,
            from_=1,
            to=10,
            textvariable=self.crossfade_duration_var,
            font=('Arial', 9),
            bg='#2d2d2d',
            fg='#ffffff',
            width=5
        )
        crossfade_spinbox.pack(anchor=tk.W, pady=(5, 0))
        
        return frame
    
    def create_interface_tab(self):
        frame = tk.Frame(self.notebook, bg='#1e1e1e')
        
        # Theme
        tk.Label(
            frame,
            text="Theme:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.theme_var = tk.StringVar(value=self.settings.get('interface.theme', 'dark'))
        theme_combo = ttk.Combobox(
            frame,
            textvariable=self.theme_var,
            values=['dark', 'light', 'blue', 'green'],
            state='readonly'
        )
        theme_combo.pack(fill=tk.X, pady=(0, 15))
        
        # Font size
        tk.Label(
            frame,
            text="Font Size:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.font_size_var = tk.IntVar(value=self.settings.get('interface.font_size', 11))
        font_size_spinbox = tk.Spinbox(
            frame,
            from_=8,
            to=20,
            textvariable=self.font_size_var,
            font=('Arial', 10),
            bg='#2d2d2d',
            fg='#ffffff',
            width=5
        )
        font_size_spinbox.pack(anchor=tk.W, pady=(0, 15))
        
        # Show album art
        self.show_album_art_var = tk.BooleanVar(value=self.settings.get('interface.show_album_art', True))
        album_art_cb = tk.Checkbutton(
            frame,
            text="Show album art when available",
            variable=self.show_album_art_var,
            font=('Arial', 10),
            fg='#ffffff',
            bg='#1e1e1e',
            selectcolor='#2d2d2d'
        )
        album_art_cb.pack(anchor=tk.W, pady=5)
        
        return frame
    
    def create_youtube_tab(self):
        frame = tk.Frame(self.notebook, bg='#1e1e1e')
        
        # API Key
        tk.Label(
            frame,
            text="YouTube API Key:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.api_key_var = tk.StringVar(value=self.settings.get('youtube.api_key', ''))
        api_key_entry = tk.Entry(
            frame,
            textvariable=self.api_key_var,
            font=('Arial', 10),
            bg='#2d2d2d',
            fg='#ffffff',
            insertbackground='white',
            show='•'
        )
        api_key_entry.pack(fill=tk.X, pady=(0, 15))
        
        # Show/hide API key button
        def toggle_api_key_visibility():
            current_show = api_key_entry.cget('show')
            api_key_entry.config(show='' if current_show == '•' else '•')
        
        toggle_btn = tk.Button(
            frame,
            text="Show/Hide API Key",
            command=toggle_api_key_visibility,
            font=('Arial', 8),
            bg='#757575',
            fg='white',
            relief='flat'
        )
        toggle_btn.pack(anchor=tk.W, pady=(0, 15))
        
        # Max search results
        tk.Label(
            frame,
            text="Maximum Search Results:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.max_results_var = tk.IntVar(value=self.settings.get('youtube.max_search_results', 10))
        max_results_spinbox = tk.Spinbox(
            frame,
            from_=5,
            to=50,
            textvariable=self.max_results_var,
            font=('Arial', 10),
            bg='#2d2d2d',
            fg='#ffffff',
            width=5
        )
        max_results_spinbox.pack(anchor=tk.W, pady=(0, 15))
        
        return frame
    
    def create_playback_tab(self):
        frame = tk.Frame(self.notebook, bg='#1e1e1e')
        
        # Repeat mode
        tk.Label(
            frame,
            text="Repeat Mode:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.repeat_var = tk.StringVar(value=self.settings.get('playback.repeat_mode', 'none'))
        repeat_combo = ttk.Combobox(
            frame,
            textvariable=self.repeat_var,
            values=['none', 'one', 'all'],
            state='readonly'
        )
        repeat_combo.pack(fill=tk.X, pady=(0, 15))
        
        # Shuffle
        self.shuffle_var = tk.BooleanVar(value=self.settings.get('playback.shuffle', False))
        shuffle_cb = tk.Checkbutton(
            frame,
            text="Shuffle playlist",
            variable=self.shuffle_var,
            font=('Arial', 10),
            fg='#ffffff',
            bg='#1e1e1e',
            selectcolor='#2d2d2d'
        )
        shuffle_cb.pack(anchor=tk.W, pady=5)
        
        # Resume playback
        self.resume_var = tk.BooleanVar(value=self.settings.get('playback.resume_playback', True))
        resume_cb = tk.Checkbutton(
            frame,
            text="Resume playback on startup",
            variable=self.resume_var,
            font=('Arial', 10),
            fg='#ffffff',
            bg='#1e1e1e',
            selectcolor='#2d2d2d'
        )
        resume_cb.pack(anchor=tk.W, pady=5)
        
        return frame
    
    def create_account_tab(self):
        frame = tk.Frame(self.notebook, bg='#1e1e1e')
        
        if self.auth.current_user:
            # User is logged in
            tk.Label(
                frame,
                text=f"Logged in as: {self.auth.current_user}",
                font=('Arial', 12, 'bold'),
                fg='#ffffff',
                bg='#1e1e1e',
                pady=20
            ).pack()
            
            logout_btn = tk.Button(
                frame,
                text="Logout",
                command=self.logout,
                font=('Arial', 10, 'bold'),
                bg='#d32f2f',
                fg='white',
                relief='flat',
                padx=20,
                pady=10
            )
            logout_btn.pack(pady=10)
            
            # User statistics
            user_data = self.auth.get_current_user_data()
            playlists_count = len(user_data.get('playlists', []))
            
            stats_frame = tk.Frame(frame, bg='#1e1e1e')
            stats_frame.pack(pady=20)
            
            tk.Label(
                stats_frame,
                text="Your Statistics:",
                font=('Arial', 11, 'bold'),
                fg='#ffffff',
                bg='#1e1e1e'
            ).pack(anchor=tk.W)
            
            tk.Label(
                stats_frame,
                text=f"Saved Playlists: {playlists_count}",
                font=('Arial', 10),
                fg='#cccccc',
                bg='#1e1e1e'
            ).pack(anchor=tk.W, pady=(5, 0))
            
        else:
            # User is not logged in
            tk.Label(
                frame,
                text="Not logged in",
                font=('Arial', 12),
                fg='#cccccc',
                bg='#1e1e1e',
                pady=20
            ).pack()
            
            login_btn = tk.Button(
                frame,
                text="Login / Register",
                command=self.show_login,
                font=('Arial', 10, 'bold'),
                bg='#007acc',
                fg='white',
                relief='flat',
                padx=20,
                pady=10
            )
            login_btn.pack(pady=10)
        
        return frame
    
    def show_login(self):
        from auth import LoginWindow
        self.window.destroy()
        LoginWindow(self.parent, self.auth, lambda username: self.parent.show_main_interface())
    
    def logout(self):
        self.auth.logout()
        self.window.destroy()
        # Refresh the settings window to show login state
        SettingsWindow(self.parent, self.settings, self.auth)
    
    def save_all_settings(self):
        # Audio settings
        self.settings.set('audio.volume', self.volume_var.get())
        self.settings.set('audio.autoplay', self.autoplay_var.get())
        self.settings.set('audio.crossfade', self.crossfade_var.get())
        self.settings.set('audio.crossfade_duration', self.crossfade_duration_var.get())
        
        # Interface settings
        self.settings.set('interface.theme', self.theme_var.get())
        self.settings.set('interface.font_size', self.font_size_var.get())
        self.settings.set('interface.show_album_art', self.show_album_art_var.get())
        
        # YouTube settings
        self.settings.set('youtube.api_key', self.api_key_var.get())
        self.settings.set('youtube.max_search_results', self.max_results_var.get())
        
        # Playback settings
        self.settings.set('playback.repeat_mode', self.repeat_var.get())
        self.settings.set('playback.shuffle', self.shuffle_var.get())
        self.settings.set('playback.resume_playback', self.resume_var.get())
        
        if self.settings.save_settings():
            messagebox.showinfo("Success", "Settings saved successfully!")
            self.window.destroy()
        else:
            messagebox.showerror("Error", "Failed to save settings!")
    
    def reset_settings(self):
        if messagebox.askyesno("Confirm", "Reset all settings to defaults?"):
            self.settings.settings = self.settings.load_default_settings()
            self.settings.save_settings()
            messagebox.showinfo("Success", "Settings reset to defaults!")
            self.window.destroy()