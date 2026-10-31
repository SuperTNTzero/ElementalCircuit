"""Start a task-owned local Blender MCP session. No user startup file or preferences are saved."""
import importlib.util
import os
from pathlib import Path
import bpy

os.environ['DISABLE_TELEMETRY']='true'
tooling=Path(__file__).resolve().parents[2]/'tooling'
spec=importlib.util.spec_from_file_location('ec_blender_mcp',tooling/'blender-mcp/addon.py')
addon=importlib.util.module_from_spec(spec)
spec.loader.exec_module(addon)
addon.register()
server=addon.BlenderMCPServer(host='127.0.0.1',port=19876)
server.start()
bpy.app.driver_namespace['ec_mcp_server']=server
print('EC_BLENDER_MCP_READY: 127.0.0.1:19876',flush=True)
