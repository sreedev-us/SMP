# auth.py
import tkinter as tk
from tkinter import ttk, messagebox
import json
import hashlib
import os
from pathlib import Path

class Authentication:
    def __init__(self):
        self.users_file = Path("users.json")
        self.current_user = None
        self.load_users()
    
    def load_users(self):
        """Load user data from JSON file"""
        if self.users_file.exists():
            try:
                with open(self.users_file, 'r') as f:
                    self.users = json.load(f)
            except:
                self.users = {}
        else:
            self.users = {}
    
    def save_users(self):
        """Save user data to JSON file"""
        with open(self.users_file, 'w') as f:
            json.dump(self.users, f, indent=2)
    
    def hash_password(self, password):
        """Hash password for security"""
        return hashlib.sha256(password.encode()).hexdigest()
    
    def register(self, username, password, email=""):
        """Register a new user"""
        if username in self.users:
            return False, "Username already exists"
        
        if len(password) < 6:
            return False, "Password must be at least 6 characters"
        
        self.users[username] = {
            'password': self.hash_password(password),
            'email': email,
            'playlists': [],
            'preferences': {}
        }
        self.save_users()
        return True, "Registration successful"
    
    def login(self, username, password):
        """Authenticate user"""
        if username not in self.users:
            return False, "Username not found"
        
        if self.users[username]['password'] != self.hash_password(password):
            return False, "Invalid password"
        
        self.current_user = username
        return True, "Login successful"
    
    def logout(self):
        """Logout current user"""
        self.current_user = None
    
    def get_current_user_data(self):
        """Get data for current user"""
        if self.current_user:
            return self.users.get(self.current_user, {})
        return {}

class LoginWindow:
    def __init__(self, parent, auth_system, on_login_success):
        self.parent = parent
        self.auth = auth_system
        self.on_login_success = on_login_success
        
        self.window = tk.Toplevel(parent)
        self.window.title("Login - Harmony Music Player")
        self.window.geometry("400x500")
        self.window.configure(bg='#1e1e1e')
        self.window.resizable(False, False)
        
        # Make modal
        self.window.transient(parent)
        self.window.grab_set()
        
        self.create_login_ui()
    
    def create_login_ui(self):
        # Main container
        main_frame = tk.Frame(self.window, bg='#1e1e1e', padx=30, pady=30)
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Title
        title_label = tk.Label(
            main_frame,
            text="Harmony Music Player",
            font=('Arial', 20, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e',
            pady=20
        )
        title_label.pack()
        
        subtitle_label = tk.Label(
            main_frame,
            text="Login to Your Account",
            font=('Arial', 12),
            fg='#cccccc',
            bg='#1e1e1e',
            pady=10
        )
        subtitle_label.pack()
        
        # Login frame
        login_frame = tk.Frame(main_frame, bg='#1e1e1e')
        login_frame.pack(fill=tk.X, pady=20)
        
        # Username
        tk.Label(
            login_frame,
            text="Username:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.username_var = tk.StringVar()
        username_entry = tk.Entry(
            login_frame,
            textvariable=self.username_var,
            font=('Arial', 11),
            bg='#2d2d2d',
            fg='#ffffff',
            insertbackground='white'
        )
        username_entry.pack(fill=tk.X, pady=(0, 15))
        
        # Password
        tk.Label(
            login_frame,
            text="Password:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.password_var = tk.StringVar()
        password_entry = tk.Entry(
            login_frame,
            textvariable=self.password_var,
            font=('Arial', 11),
            bg='#2d2d2d',
            fg='#ffffff',
            insertbackground='white',
            show='•'
        )
        password_entry.pack(fill=tk.X, pady=(0, 20))
        password_entry.bind('<Return>', lambda e: self.login())
        
        # Login button
        login_btn = tk.Button(
            login_frame,
            text="Login",
            command=self.login,
            font=('Arial', 12, 'bold'),
            bg='#007acc',
            fg='white',
            relief='flat',
            pady=10
        )
        login_btn.pack(fill=tk.X, pady=(0, 15))
        
        # Register link
        register_btn = tk.Button(
            login_frame,
            text="Don't have an account? Register",
            command=self.show_register,
            font=('Arial', 10),
            bg='#1e1e1e',
            fg='#007acc',
            relief='flat',
            border=0
        )
        register_btn.pack()
        
        # Status
        self.status_var = tk.StringVar()
        status_label = tk.Label(
            login_frame,
            textvariable=self.status_var,
            font=('Arial', 9),
            fg='#ff6b6b',
            bg='#1e1e1e'
        )
        status_label.pack(pady=10)
    
    def login(self):
        username = self.username_var.get().strip()
        password = self.password_var.get()
        
        if not username or not password:
            self.status_var.set("Please enter both username and password")
            return
        
        success, message = self.auth.login(username, password)
        if success:
            self.on_login_success(username)
            self.window.destroy()
        else:
            self.status_var.set(message)
    
    def show_register(self):
        RegisterWindow(self.parent, self.auth, self.on_login_success)

class RegisterWindow:
    def __init__(self, parent, auth_system, on_login_success):
        self.parent = parent
        self.auth = auth_system
        self.on_login_success = on_login_success
        
        self.window = tk.Toplevel(parent)
        self.window.title("Register - Harmony Music Player")
        self.window.geometry("400x600")
        self.window.configure(bg='#1e1e1e')
        self.window.resizable(False, False)
        
        self.window.transient(parent)
        self.window.grab_set()
        
        self.create_register_ui()
    
    def create_register_ui(self):
        # Similar structure to login UI but with additional fields
        main_frame = tk.Frame(self.window, bg='#1e1e1e', padx=30, pady=30)
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        title_label = tk.Label(
            main_frame,
            text="Create Account",
            font=('Arial', 20, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e',
            pady=20
        )
        title_label.pack()
        
        register_frame = tk.Frame(main_frame, bg='#1e1e1e')
        register_frame.pack(fill=tk.X, pady=20)
        
        # Username
        tk.Label(
            register_frame,
            text="Username:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.username_var = tk.StringVar()
        username_entry = tk.Entry(
            register_frame,
            textvariable=self.username_var,
            font=('Arial', 11),
            bg='#2d2d2d',
            fg='#ffffff',
            insertbackground='white'
        )
        username_entry.pack(fill=tk.X, pady=(0, 15))
        
        # Email
        tk.Label(
            register_frame,
            text="Email (optional):",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.email_var = tk.StringVar()
        email_entry = tk.Entry(
            register_frame,
            textvariable=self.email_var,
            font=('Arial', 11),
            bg='#2d2d2d',
            fg='#ffffff',
            insertbackground='white'
        )
        email_entry.pack(fill=tk.X, pady=(0, 15))
        
        # Password
        tk.Label(
            register_frame,
            text="Password:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.password_var = tk.StringVar()
        password_entry = tk.Entry(
            register_frame,
            textvariable=self.password_var,
            font=('Arial', 11),
            bg='#2d2d2d',
            fg='#ffffff',
            insertbackground='white',
            show='•'
        )
        password_entry.pack(fill=tk.X, pady=(0, 15))
        
        # Confirm Password
        tk.Label(
            register_frame,
            text="Confirm Password:",
            font=('Arial', 10, 'bold'),
            fg='#ffffff',
            bg='#1e1e1e'
        ).pack(anchor=tk.W, pady=(10, 5))
        
        self.confirm_password_var = tk.StringVar()
        confirm_password_entry = tk.Entry(
            register_frame,
            textvariable=self.confirm_password_var,
            font=('Arial', 11),
            bg='#2d2d2d',
            fg='#ffffff',
            insertbackground='white',
            show='•'
        )
        confirm_password_entry.pack(fill=tk.X, pady=(0, 20))
        confirm_password_entry.bind('<Return>', lambda e: self.register())
        
        # Register button
        register_btn = tk.Button(
            register_frame,
            text="Register",
            command=self.register,
            font=('Arial', 12, 'bold'),
            bg='#388e3c',
            fg='white',
            relief='flat',
            pady=10
        )
        register_btn.pack(fill=tk.X, pady=(0, 15))
        
        # Back to login
        back_btn = tk.Button(
            register_frame,
            text="Back to Login",
            command=self.window.destroy,
            font=('Arial', 10),
            bg='#1e1e1e',
            fg='#007acc',
            relief='flat',
            border=0
        )
        back_btn.pack()
        
        # Status
        self.status_var = tk.StringVar()
        status_label = tk.Label(
            register_frame,
            textvariable=self.status_var,
            font=('Arial', 9),
            fg='#ff6b6b',
            bg='#1e1e1e'
        )
        status_label.pack(pady=10)
    
    def register(self):
        username = self.username_var.get().strip()
        password = self.password_var.get()
        confirm_password = self.confirm_password_var.get()
        email = self.email_var.get().strip()
        
        if not username or not password:
            self.status_var.set("Please enter username and password")
            return
        
        if password != confirm_password:
            self.status_var.set("Passwords do not match")
            return
        
        success, message = self.auth.register(username, password, email)
        if success:
            # Auto-login after registration
            login_success, login_message = self.auth.login(username, password)
            if login_success:
                self.on_login_success(username)
                self.window.destroy()
            else:
                self.status_var.set(f"Registration successful but login failed: {login_message}")
        else:
            self.status_var.set(message)