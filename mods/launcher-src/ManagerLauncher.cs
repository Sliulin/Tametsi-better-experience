using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Text.RegularExpressions;
using System.Windows.Forms;

[assembly: AssemblyTitle("Tametsi Mod Manager")]
[assembly: AssemblyDescription("Tametsi Mod Manager launcher")]
[assembly: AssemblyProduct("Tametsi Mod Manager")]
[assembly: AssemblyVersion("3.0.0.0")]
[assembly: AssemblyFileVersion("3.0.0.0")]

internal static class ManagerLauncher
{
    [STAThread]
    private static int Main()
    {
        try
        {
            string root = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
            string mods = Path.Combine(root, "mods");
            string game = Path.Combine(root, "tametsi.exe");
            string java = Path.Combine(root, "jre", "bin", "javaw.exe");
            string asm = Path.Combine(mods, "lib", "asm-9.7.1.jar");
            if (!File.Exists(game) || !File.Exists(java))
                throw new Exception("请将管理器和 mods 文件夹一起放到 tametsi.exe 所在目录，并保留原版 jre 文件夹。");
            string selected = File.ReadAllText(Path.Combine(mods, "active-runtime.txt")).Trim();
            if (!Regex.IsMatch(selected, @"\Aversions/[A-Za-z0-9._-]+\.jar\z"))
                throw new Exception("Mod 版本指针无效，请重新解压完整 Mod 包。");
            string runtime = Path.Combine(mods, selected.Replace('/', Path.DirectorySeparatorChar));
            if (!File.Exists(runtime) || !File.Exists(asm))
                throw new Exception("缺少 Mod 运行文件，请重新解压完整 Mod 包。");
            var start = new ProcessStartInfo(java);
            start.WorkingDirectory = root;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.Arguments = "-cp \"" + runtime + ";" + asm + ";" + game + "\" tametsimod.Manager";
            using (Process process = Process.Start(start))
            {
                if (process == null) throw new Exception("Java 管理器进程未能启动。");
            }
            return 0;
        }
        catch (Exception error)
        {
            MessageBox.Show("Mod 管理器启动失败：\n" + error.Message, "Tametsi Mod", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }
}
