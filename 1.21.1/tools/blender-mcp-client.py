"""Invoke the downloaded MCP server over stdio; use only the local Blender connection."""
import asyncio
import json
import os
import sys
from pathlib import Path
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

async def main():
    root=Path(__file__).resolve().parents[1]
    env=dict(os.environ,BLENDER_HOST='127.0.0.1',BLENDER_PORT='19876',DISABLE_TELEMETRY='true',PYTHONIOENCODING='utf-8')
    # Prevent all optional trajectory capture and external asset providers.
    env['BLENDER_MCP_DISABLE_TELEMETRY']='true'
    params=StdioServerParameters(command=sys.executable,args=[str(root/'tools/blender-mcp-server.py')],env=env)
    async with stdio_client(params) as (read,write):
        async with ClientSession(read,write) as session:
            await session.initialize()
            if len(sys.argv)<2:
                result=await session.call_tool('get_addon_status',{})
            else:
                script=Path(sys.argv[1]).resolve()
                code=script.read_text(encoding='utf-8')
                # __file__ is not supplied by MCP's exec context.
                code='__file__='+repr(str(script))+'\n'+code
                result=await session.call_tool('execute_blender_code',{'code':code,'user_prompt':'制作三维模块化法杖并导出Minecraft模型'})
            for item in result.content:
                if hasattr(item,'text'):
                    print(item.text)
                    if item.text.lower().startswith(('error','failed')):raise SystemExit(1)
            if result.isError:raise SystemExit(1)

asyncio.run(main())
