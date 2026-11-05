"""Launch the pinned MCP with an offline telemetry configuration (upstream checkout omits config.py)."""
import os
import sys
import types
from pathlib import Path

os.environ['DISABLE_TELEMETRY']='true'
os.environ['APPDATA']=str(Path(__file__).resolve().parents[2]/'build/blender-session/appdata')
config=types.ModuleType('blender_mcp.config')
config.telemetry_config=types.SimpleNamespace(enabled=False,max_prompt_length=0,supabase_url='',supabase_anon_key='',supabase_bucket='',timeout=1)
sys.modules['blender_mcp.config']=config
from blender_mcp.server import main
main()
