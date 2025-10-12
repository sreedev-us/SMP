import tkinter as tk
from tkinter import ttk, filedialog, messagebox, scrolledtext
import os
import time
import threading
import tempfile
import vlc
import pafy
from googleapiclient.discovery import build

class MusicPlayer:
    def __init__(self, root,auth_system,settings_manager):
        self.root = root
        self.auth = auth_system
        self.settings = settings_manager
        
        # Use settings
        self.volume = self.settings.get('audio.volume', 70)
        
   
        self.root.title("Music Player")
        self.root.geometry("1100x750")
        self.root.configure(bg='#1e1e1e')
        
        # YouTube API configuration
        self.YOUTUBE_API_KEY = "AIzaSyC4qIUVD-kQQ1dsMFAHRa7qEgqG5OQ0J2Q"
        self.youtube_api = None
        self.initialize_youtube_api()
        
        # VLC and YouTube variables
        self.instance = vlc.Instance()
        self.player = self.instance.media_player_new()
        self.current_media = None
        self.is_playing = False
        self.is_paused = False
        self.volume = 70
        
        # Music variables
        self.playlist = []
        self.playlist_data = []
        self.current_song_index = 0
        self.temp_files = []
        self.search_results = []
        
        # Create UI
        self.create_ui()
        
        # Bind closing event
        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)
    
    def initialize_youtube_api(self):
        """Initialize the YouTube Data API client"""
        try:
            if self.YOUTUBE_API_KEY and self.YOUTUBE_API_KEY != "YOUR_API_KEY_HERE":
                self.youtube_api = build('youtube', 'v3', developerKey=self.YOUTUBE_API_KEY)
            else:
                messagebox.showwarning("API Key Required", 
                    "Please set your YouTube Data API key in the code.\n\n"
                    "Get one from: Google Cloud Console -> YouTube Data API v3")
        except Exception as e:
            messagebox.showerror("API Error", f"Failed to initialize YouTube API: {str(e)}")
    
    def create_ui(self):
        # Main container
        main_frame = tk.Frame(self.root, bg='#1e1e1e')
        main_frame.pack(fill=tk.BOTH, expand=True, padx=20, pady=20)
        
        # Header
        header_frame = tk.Frame(main_frame, bg='#1e1e1e')
        header_frame.pack(fill=tk.X, pady=(0, 15))
        
        title_label = tk.Label(
            header_frame, 
            text="Harmony Music Player - YouTube Search", 
            font=('Arial', 18, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        )
        title_label.pack(side=tk.LEFT)
        
        # YouTube Search section
        search_section = tk.Frame(main_frame, bg='#1e1e1e')
        search_section.pack(fill=tk.X, pady=(0, 15))
        
        # Search input frame
        search_input_frame = tk.Frame(search_section, bg='#1e1e1e')
        search_input_frame.pack(fill=tk.X, pady=(0, 10))
        
        tk.Label(
            search_input_frame,
            text="Search YouTube:",
            font=('Arial', 11, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(side=tk.LEFT)
        
        self.search_var = tk.StringVar()
        search_entry = tk.Entry(
            search_input_frame,
            textvariable=self.search_var,
            font=('Arial', 11),
            width=40,
            bg='#2d2d2d',
            fg='#ffffff',
            insertbackground='white'
        )
        search_entry.pack(side=tk.LEFT, padx=(10, 10))
        search_entry.bind('<Return>', self.search_youtube)
        
        search_btn = tk.Button(
            search_input_frame,
            text="Search",
            command=self.search_youtube,
            font=('Arial', 10, 'bold'),
            bg='#388e3c',
            fg='white',
            relief='flat',
            padx=20
        )
        search_btn.pack(side=tk.LEFT)
        
        # Search results frame
        search_results_frame = tk.Frame(search_section, bg='#1e1e1e')
        search_results_frame.pack(fill=tk.X)
        
        tk.Label(
            search_results_frame,
            text="Search Results:",
            font=('Arial', 11, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W)
        
        # Search results listbox with scrollbar
        results_container = tk.Frame(search_results_frame, bg='#1e1e1e')
        results_container.pack(fill=tk.X, pady=(5, 0))
        
        self.search_results_listbox = tk.Listbox(
            results_container,
            bg='#2d2d2d',
            fg='#ffffff',
            selectbackground='#388e3c',
            selectforeground='white',
            font=('Arial', 10),
            height=6
        )
        
        results_scrollbar = tk.Scrollbar(results_container, orient=tk.VERTICAL)
        results_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        
        self.search_results_listbox.config(yscrollcommand=results_scrollbar.set)
        results_scrollbar.config(command=self.search_results_listbox.yview)
        
        self.search_results_listbox.pack(side=tk.LEFT, fill=tk.X, expand=True)
        self.search_results_listbox.bind('<Double-Button-1>', self.add_selected_search_result)
        
        # Search results buttons
        results_btn_frame = tk.Frame(search_results_frame, bg='#1e1e1e')
        results_btn_frame.pack(fill=tk.X, pady=(5, 0))
        
        add_selected_btn = tk.Button(
            results_btn_frame,
            text="Add Selected to Playlist",
            command=self.add_selected_search_result,
            font=('Arial', 9, 'bold'),
            bg='#007acc',
            fg='white',
            relief='flat',
            padx=15
        )
        add_selected_btn.pack(side=tk.LEFT, padx=(0, 10))
        
        clear_results_btn = tk.Button(
            results_btn_frame,
            text="Clear Results",
            command=self.clear_search_results,
            font=('Arial', 9, 'bold'),
            bg='#ff9800',
            fg='white',
            relief='flat',
            padx=15
        )
        clear_results_btn.pack(side=tk.LEFT)
        
        # Content area
        content_frame = tk.Frame(main_frame, bg='#1e1e1e')
        content_frame.pack(fill=tk.BOTH, expand=True)
        
        # Left panel - Playlist
        left_frame = tk.Frame(content_frame, bg='#252526', width=400)
        left_frame.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))
        left_frame.pack_propagate(False)
        
        playlist_label = tk.Label(
            left_frame,
            text="Playlist",
            font=('Arial', 16, 'bold'),
            fg='#ffffff',
            bg='#252526',
            pady=10
        )
        playlist_label.pack(fill=tk.X)
        
        # Playlist listbox with scrollbar
        listbox_frame = tk.Frame(left_frame, bg='#252526')
        listbox_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=(0, 10))
        
        self.playlist_box = tk.Listbox(
            listbox_frame,
            bg='#1e1e1e',
            fg='#ffffff',
            selectbackground='#007acc',
            selectforeground='white',
            font=('Arial', 11),
            relief='flat'
        )
        
        scrollbar = tk.Scrollbar(listbox_frame, orient=tk.VERTICAL)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        
        self.playlist_box.config(yscrollcommand=scrollbar.set)
        scrollbar.config(command=self.playlist_box.yview)
        
        self.playlist_box.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.playlist_box.bind('<<ListboxSelect>>', self.on_song_select)
        self.playlist_box.bind('<Double-Button-1>', self.on_song_double_click)
        
        # Right panel - Player controls (keep your existing player controls here)
        right_frame = tk.Frame(content_frame, bg='#1e1e1e')
        right_frame.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True)
        
        # Now playing section
        now_playing_frame = tk.Frame(right_frame, bg='#252526')
        now_playing_frame.pack(fill=tk.X, pady=(0, 20))
        
        now_playing_label = tk.Label(
            now_playing_frame,
            text="Now Playing",
            font=('Arial', 16, 'bold'),
            fg='#ffffff',
            bg='#252526',
            pady=15
        )
        now_playing_label.pack()
        
        self.song_info_label = tk.Label(
            now_playing_frame,
            text="No song selected",
            font=('Arial', 14),
            fg='#cccccc',
            bg='#252526',
            pady=10,
            wraplength=500
        )
        self.song_info_label.pack()
        
        # Progress section
        progress_frame = tk.Frame(right_frame, bg='#1e1e1e')
        progress_frame.pack(fill=tk.X, pady=20)
        
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Scale(
            progress_frame,
            from_=0,
            to=100,
            variable=self.progress_var,
            orient=tk.HORIZONTAL,
            command=self.seek,
            length=500
        )
        self.progress_bar.pack(fill=tk.X, pady=10)
        
        self.time_label = tk.Label(
            progress_frame,
            text="00:00 / 00:00",
            font=('Arial', 12),
            fg='#cccccc',
            bg='#1e1e1e'
        )
        self.time_label.pack()
        
        # Controls section
        controls_frame = tk.Frame(right_frame, bg='#1e1e1e')
        controls_frame.pack(fill=tk.X, pady=20)
        
        btn_style = {
            'font': ('Arial', 14, 'bold'),
            'bg': '#007acc',
            'fg': 'white',
            'relief': 'flat',
            'width': 6,
            'height': 2
        }
        
        prev_btn = tk.Button(controls_frame, text="⏮", command=self.previous_song, **btn_style)
        prev_btn.pack(side=tk.LEFT, padx=15)
        
        self.play_btn = tk.Button(controls_frame, text="▶", command=self.toggle_play, **btn_style)
        self.play_btn.pack(side=tk.LEFT, padx=15)
        
        next_btn = tk.Button(controls_frame, text="⏭", command=self.next_song, **btn_style)
        next_btn.pack(side=tk.LEFT, padx=15)
        
        stop_btn = tk.Button(controls_frame, text="⏹", command=self.stop_music, **btn_style)
        stop_btn.pack(side=tk.LEFT, padx=15)
        
        # Volume control
        volume_frame = tk.Frame(right_frame, bg='#1e1e1e')
        volume_frame.pack(fill=tk.X, pady=20)
        
        volume_label = tk.Label(
            volume_frame,
            text="Volume:",
            font=('Arial', 12, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        )
        volume_label.pack(side=tk.LEFT)
        
        self.volume_var = tk.DoubleVar(value=self.volume)
        volume_scale = ttk.Scale(
            volume_frame,
            from_=0,
            to=100,
            variable=self.volume_var,
            orient=tk.HORIZONTAL,
            command=self.set_volume,
            length=300
        )
        volume_scale.pack(side=tk.LEFT, padx=10)
        
        self.volume_percent_label = tk.Label(
            volume_frame,
            text=f"{int(self.volume)}%",
            font=('Arial', 12),
            fg='#ffffff',
            bg='#1e1e1e'
        )
        self.volume_percent_label.pack(side=tk.LEFT, padx=10)
        
        # Button frame for playlist management
        button_frame = tk.Frame(left_frame, bg='#252526')
        button_frame.pack(fill=tk.X, padx=10, pady=10)
        
        add_songs_btn = tk.Button(
            button_frame,
            text="Add Local Songs",
            command=self.add_songs,
            font=('Arial', 11, 'bold'),
            bg='#007acc',
            fg='white',
            relief='flat',
            pady=8
        )
        add_songs_btn.pack(fill=tk.X, pady=(0, 5))
        
        remove_btn = tk.Button(
            button_frame,
            text="Remove Selected",
            command=self.remove_selected,
            font=('Arial', 11, 'bold'),
            bg='#d32f2f',
            fg='white',
            relief='flat',
            pady=8
        )
        remove_btn.pack(fill=tk.X, pady=(0, 5))
        
        clear_btn = tk.Button(
            button_frame,
            text="Clear Playlist",
            command=self.clear_playlist,
            font=('Arial', 11, 'bold'),
            bg='#ff9800',
            fg='white',
            relief='flat',
            pady=8
        )
        clear_btn.pack(fill=tk.X)
        
        # Status bar
        self.status_var = tk.StringVar(value="Ready to play music - Set your API key first!")
        status_bar = tk.Label(
            self.root,
            textvariable=self.status_var,
            font=('Arial', 10),
            fg='#cccccc',
            bg='#007acc',
            anchor=tk.W,
            padx=10
        )
        status_bar.pack(side=tk.BOTTOM, fill=tk.X)
        
        # Set initial volume
        self.player.audio_set_volume(int(self.volume))
        
        # Start progress updater
        self.update_progress()
        
        # Bind keyboard shortcuts
        self.setup_keyboard_shortcuts()
    
    def search_youtube(self, event=None):
        """Search for songs on YouTube using the Data API"""
        if not self.youtube_api:
            messagebox.showerror("API Error", "YouTube API not initialized. Please check your API key.")
            return
        
        query = self.search_var.get().strip()
        if not query:
            messagebox.showwarning("Warning", "Please enter a search term")
            return
        
        self.status_var.set("Searching YouTube...")
        
        # Clear previous results
        self.search_results_listbox.delete(0, tk.END)
        self.search_results_listbox.insert(tk.END, "Searching... Please wait.")
        
        # Search in a separate thread to prevent UI freezing
        threading.Thread(target=self.execute_youtube_search, args=(query,), daemon=True).start()
    
    def execute_youtube_search(self, query):
        """Execute YouTube search in background thread"""
        try:
            # Make API request to search for videos [citation:1][citation:3]
            search_response = self.youtube_api.search().list(
                q=query,
                part='id,snippet',
                type='video',
                maxResults=10,
                order='relevance'
            ).execute()
            
            self.search_results = []
            
            for item in search_response['items']:
                video_data = {
                    'video_id': item['id']['videoId'],
                    'title': item['snippet']['title'],
                    'channel': item['snippet']['channelTitle'],
                    'url': f"https://www.youtube.com/watch?v={item['id']['videoId']}"
                }
                self.search_results.append(video_data)
            
            # Update UI in main thread
            self.root.after(0, self.display_search_results)
            
        except Exception as e:
            error_msg = f"YouTube search failed: {str(e)}"
            self.root.after(0, lambda: self.show_search_error(error_msg))
    
    def display_search_results(self):
        """Display search results in the listbox"""
        self.search_results_listbox.delete(0, tk.END)
        
        if not self.search_results:
            self.search_results_listbox.insert(tk.END, "No results found")
            self.status_var.set("No results found")
            return
        
        for i, video in enumerate(self.search_results):
            # Truncate long titles for display
            title = video['title'][:60] + "..." if len(video['title']) > 60 else video['title']
            display_text = f"{title} - {video['channel']}"
            self.search_results_listbox.insert(tk.END, display_text)
        
        self.status_var.set(f"Found {len(self.search_results)} results")
    
    def show_search_error(self, error_msg):
        """Display search error in the listbox"""
        self.search_results_listbox.delete(0, tk.END)
        self.search_results_listbox.insert(tk.END, f"Search failed: {error_msg}")
        self.status_var.set("Search failed")
    
    def add_selected_search_result(self, event=None):
        """Add selected search result to playlist"""
        if not self.search_results:
            return
        
        selection = self.search_results_listbox.curselection()
        if not selection:
            messagebox.showinfo("Info", "Please select a song from search results")
            return
        
        index = selection[0]
        if index >= len(self.search_results):
            return
        
        video = self.search_results[index]
        
        # Show processing message
        self.status_var.set(f"Processing: {video['title']}")
        
        # Process in background thread
        threading.Thread(target=self.process_and_add_video, args=(video,), daemon=True).start()
    
    def process_and_add_video(self, video):
        """Process YouTube video and add to playlist in background"""
        try:
            # Use pafy to get streamable URL
            yt_video = pafy.new(video['url'])
            best_audio = yt_video.getbestaudio()
            stream_url = best_audio.url
            
            song_data = {
                'type': 'youtube',
                'path': stream_url,
                'display_name': f"YouTube: {video['title']}",
                'title': video['title'],
                'url': video['url']
            }
            
            # Update UI in main thread
            self.root.after(0, lambda: self.finalize_add_to_playlist(song_data, video['title']))
            
        except Exception as e:
            error_msg = f"Failed to process video: {str(e)}"
            self.root.after(0, lambda: messagebox.showerror("Error", error_msg))
            self.root.after(0, lambda: self.status_var.set("Failed to add song"))
    
    def finalize_add_to_playlist(self, song_data, title):
        """Finalize adding song to playlist in main thread"""
        self.playlist_data.append(song_data)
        self.playlist.append(song_data['path'])
        self.update_playlist_display()
        self.status_var.set(f"Added: {title}")
    
    def clear_search_results(self):
        """Clear search results"""
        self.search_results_listbox.delete(0, tk.END)
        self.search_results = []
        self.status_var.set("Search results cleared")
    
    # Keep all your existing methods (add_songs, update_playlist_display, on_song_select, 
    # on_song_double_click, play_music, toggle_play, stop_music, next_song, previous_song,
    # set_volume, seek, update_progress, remove_selected, clear_playlist, setup_keyboard_shortcuts,
    # cleanup_temp_files, on_closing) exactly as they were in your previous VLC implementation

    # [Include all your existing methods here without changes...]
    def update_playlist_display(self):
        self.playlist_box.delete(0, tk.END)
        for song_data in self.playlist_data:
            self.playlist_box.insert(tk.END, song_data['display_name'])

    def on_song_select(self, event):
        if self.playlist_box.curselection():
            index = self.playlist_box.curselection()[0]
            selected_song_name = self.playlist_box.get(index)
            for i, song_data in enumerate(self.playlist_data):
                if song_data['display_name'] == selected_song_name:
                    self.current_song_index = i
                    break

    def on_song_double_click(self, event):
        self.on_song_select(event)
        self.play_music()

    
    def play_music(self):
        if not self.playlist_data:
            messagebox.showinfo("Info", "Playlist is empty. Add some songs first.")
            return
        
        try:
            song_data = self.playlist_data[self.current_song_index]
            
            # Stop any currently playing media
            self.player.stop()
            
            # Create new media from the path
            self.current_media = self.instance.media_new(song_data['path'])
            self.player.set_media(self.current_media)
            
            # Play the media
            self.player.play()
            self.is_playing = True
            self.is_paused = False
            self.play_btn.config(text="⏸")
            
            # Update UI
            self.song_info_label.config(text=song_data['display_name'])
            self.status_var.set(f"Now playing: {song_data['title']}")
            
            # Update playlist selection
            self.update_playlist_selection(song_data['display_name'])
            
        except Exception as e:
            messagebox.showerror("Error", f"Could not play the song: {str(e)}")

    def update_playlist_selection(self, display_name):
        for i in range(self.playlist_box.size()):
            if self.playlist_box.get(i) == display_name:
                self.playlist_box.selection_clear(0, tk.END)
                self.playlist_box.selection_set(i)
                self.playlist_box.activate(i)
                break

    def toggle_play(self):
        if not self.playlist_data:
            return
        
        if not self.is_playing:
            if self.is_paused:
                # Resume playback
                self.player.set_pause(0)
                self.is_paused = False
                self.is_playing = True
                self.play_btn.config(text="⏸")
                self.status_var.set("Resumed playback")
            else:
                # Start new playback
                self.play_music()
        else:
            # Pause playback
            self.player.set_pause(1)
            self.is_paused = True
            self.is_playing = False
            self.play_btn.config(text="▶")
            self.status_var.set("Playback paused")

    def stop_music(self):
        self.player.stop()
        self.is_playing = False
        self.is_paused = False
        self.play_btn.config(text="▶")
        self.progress_var.set(0)
        self.time_label.config(text="00:00 / 00:00")
        self.status_var.set("Playback stopped")

    def next_song(self):
        if not self.playlist_data:
            return
        
        self.current_song_index = (self.current_song_index + 1) % len(self.playlist_data)
        self.play_music()

    def previous_song(self):
        if not self.playlist_data:
            return
        
        self.current_song_index = (self.current_song_index - 1) % len(self.playlist_data)
        self.play_music()

    def set_volume(self, val):
        self.volume = float(val)
        self.player.audio_set_volume(int(self.volume))
        self.volume_percent_label.config(text=f"{int(self.volume)}%")

    def seek(self, val):
        if self.is_playing and self.player.is_playing():
            media = self.player.get_media()
            if media:
                media.parse()
                duration = media.get_duration()
                if duration > 0:
                    new_time = duration * (float(val) / 100)
                    self.player.set_time(int(new_time))

    def update_progress(self):
        if self.is_playing and self.player.is_playing():
            try:
                current_time = self.player.get_time()
                duration = self.player.get_length()
                
                if duration > 0:
                    progress_percent = (current_time / duration) * 100
                    self.progress_var.set(progress_percent)
                    
                    current_mins, current_secs = divmod(current_time // 1000, 60)
                    duration_mins, duration_secs = divmod(duration // 1000, 60)
                    self.time_label.config(text=f"{current_mins:02d}:{current_secs:02d} / {duration_mins:02d}:{duration_secs:02d}")
                
            except Exception:
                pass
        
        self.root.after(1000, self.update_progress)

    def add_songs(self):
        files = filedialog.askopenfilenames(
            title="Select music files",
            filetypes=[("Audio Files", "*.mp3 *.wav *.ogg *.m4a *.flac")]
        )
        
        if files:
            for file in files:
                song_data = {
                    'type': 'local',
                    'path': file,
                    'display_name': os.path.basename(file),
                    'title': os.path.basename(file)
                }
                
                if song_data not in self.playlist_data:
                    self.playlist_data.append(song_data)
                    self.playlist.append(file)
            
            self.update_playlist_display()
            self.status_var.set(f"Added {len(files)} local songs to playlist")

    def remove_selected(self):
        if not self.playlist_box.curselection():
            messagebox.showinfo("Info", "Please select a song to remove")
            return
        
        index = self.playlist_box.curselection()[0]
        selected_song_name = self.playlist_box.get(index)
        
        for i, song_data in enumerate(self.playlist_data):
            if song_data['display_name'] == selected_song_name:
                if i < len(self.playlist):
                    self.playlist.pop(i)
                self.playlist_data.pop(i)
                
                if i == self.current_song_index:
                    self.stop_music()
                break
        
        self.update_playlist_display()
        self.status_var.set("Song removed from playlist")

    def clear_playlist(self):
        if not self.playlist_data:
            return
        
        if messagebox.askyesno("Confirm", "Clear entire playlist?"):
            self.playlist.clear()
            self.playlist_data.clear()
            self.update_playlist_display()
            self.stop_music()
            self.status_var.set("Playlist cleared")

    def setup_keyboard_shortcuts(self):
        self.root.bind('<space>', lambda e: self.toggle_play())
        self.root.bind('<Left>', lambda e: self.previous_song())
        self.root.bind('<Right>', lambda e: self.next_song())
        self.root.bind('<Escape>', lambda e: self.stop_music())

    def cleanup_temp_files(self):
        for temp_file in self.temp_files:
            try:
                if os.path.exists(temp_file):
                    os.unlink(temp_file)
            except Exception as e:
                print(f"Error cleaning up temp file: {e}")

    def on_closing(self):
        self.stop_music()
        self.cleanup_temp_files()
        self.root.destroy()

def main():
    root = tk.Tk()
    app = MusicPlayer(root)
    root.mainloop()

if __name__ == "__main__":
    main()