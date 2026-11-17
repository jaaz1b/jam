// Jam for Windows' link to the system media controls (the overlay of the volume keys, the lock
// screen, the taskbar's media flyout): Jam writes what it plays on stdin, one line per change, and
// reads the buttons pressed there on stdout. GPL-3.0, part of Jam (from Myspoti / Orpheon by kota-zo).
//
// In:  meta\t<title>\t<artist>\t<album>\t<cover: http(s) URL or file path, or empty>
//      state\t<playing|paused|stopped>
//      timeline\t<position ms>\t<duration ms>
//      caps\t<next 0|1>\t<previous 0|1>
// Out: play | pause | next | previous | stop | seek\t<ms>
#include <windows.h>
#include <shobjidl.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Media.Playback.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Windows.Storage.Streams.h>
#include <iostream>
#include <mutex>
#include <string>
#include <vector>

using namespace winrt;
using namespace Windows::Media;
using namespace Windows::Foundation;

static std::mutex out;
static void emit(const std::string& s) { std::lock_guard<std::mutex> l(out); std::cout << s << "\n" << std::flush; }

static std::vector<std::wstring> split(const std::wstring& s) {
    std::vector<std::wstring> v; size_t a = 0;
    for (;;) { size_t b = s.find(L'\t', a); v.push_back(s.substr(a, b == std::wstring::npos ? std::wstring::npos : b - a)); if (b == std::wstring::npos) break; a = b + 1; }
    return v;
}

static std::wstring widen(const std::string& s) {
    if (s.empty()) return {};
    int n = MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), nullptr, 0);
    std::wstring w(n, 0); MultiByteToWideChar(CP_UTF8, 0, s.data(), (int)s.size(), w.data(), n); return w;
}

int main() {
    SetCurrentProcessExplicitAppUserModelID(L"app.jam.desktop");
    init_apartment();
    Playback::MediaPlayer player;
    player.CommandManager().IsEnabled(false);
    auto smtc = player.SystemMediaTransportControls();
    smtc.IsEnabled(true);
    smtc.IsPlayEnabled(true); smtc.IsPauseEnabled(true); smtc.IsStopEnabled(true);
    smtc.IsNextEnabled(true); smtc.IsPreviousEnabled(true);
    smtc.PlaybackStatus(MediaPlaybackStatus::Closed);
    smtc.ButtonPressed([](auto&&, SystemMediaTransportControlsButtonPressedEventArgs const& e) {
        switch (e.Button()) {
            case SystemMediaTransportControlsButton::Play: emit("play"); break;
            case SystemMediaTransportControlsButton::Pause: emit("pause"); break;
            case SystemMediaTransportControlsButton::Next: emit("next"); break;
            case SystemMediaTransportControlsButton::Previous: emit("previous"); break;
            case SystemMediaTransportControlsButton::Stop: emit("stop"); break;
            default: break;
        }
    });
    smtc.PlaybackPositionChangeRequested([](auto&&, PlaybackPositionChangeRequestedEventArgs const& e) {
        emit("seek\t" + std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(e.RequestedPlaybackPosition()).count()));
    });
    emit("ready");

    std::string line;
    while (std::getline(std::cin, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        auto f = split(widen(line));
        try {
            if (f[0] == L"meta" && f.size() >= 5) {
                auto u = smtc.DisplayUpdater();
                u.Type(MediaPlaybackType::Music);
                u.MusicProperties().Title(f[1]);
                u.MusicProperties().Artist(f[2]);
                u.MusicProperties().AlbumTitle(f[3]);
                if (f[4].rfind(L"http", 0) == 0) u.Thumbnail(Windows::Storage::Streams::RandomAccessStreamReference::CreateFromUri(Uri(f[4])));
                else if (!f[4].empty()) {
                    try { u.Thumbnail(Windows::Storage::Streams::RandomAccessStreamReference::CreateFromFile(Windows::Storage::StorageFile::GetFileFromPathAsync(f[4]).get())); }
                    catch (...) { u.Thumbnail(nullptr); }
                } else u.Thumbnail(nullptr);
                u.Update();
            } else if (f[0] == L"state" && f.size() >= 2) {
                smtc.PlaybackStatus(f[1] == L"playing" ? MediaPlaybackStatus::Playing : f[1] == L"paused" ? MediaPlaybackStatus::Paused : MediaPlaybackStatus::Stopped);
            } else if (f[0] == L"timeline" && f.size() >= 3) {
                SystemMediaTransportControlsTimelineProperties t;
                auto pos = std::chrono::milliseconds(std::stoll(f[1])), dur = std::chrono::milliseconds(std::stoll(f[2]));
                t.StartTime(TimeSpan(0)); t.MinSeekTime(TimeSpan(0));
                t.Position(pos); t.EndTime(dur); t.MaxSeekTime(dur);
                smtc.UpdateTimelineProperties(t);
            } else if (f[0] == L"caps" && f.size() >= 3) {
                smtc.IsNextEnabled(f[1] == L"1"); smtc.IsPreviousEnabled(f[2] == L"1");
            }
        } catch (...) {}
    }
    return 0;
}
