# Sudan: Cloudflare IPs unreachable on Sudani (3 Nov 2026)

## Evidence (measured from a phone in Sudan, Sudani LTE vs Starlink, same minute)

| | Sudani (AS15706, public IP 197.252.141.53) | Starlink |
|---|---|---|
| DNS answer for `app-content.arthurarchive.com`, `turath.io` | `188.114.96.6` / `188.114.97.6` (also from 1.1.1.1 DoH) | `104.21.84.124` / `172.67.193.3` |
| TCP 443 + ping to `188.114.96.6`, `188.114.97.6` | **timeout, 100% loss** | OK |
| TCP 443 to `188.114.96.3`, `188.114.97.7`, `104.21.84.124`, `172.67.193.3` | OK | OK |

Cloudflare's DNS answers vary between those sets, so sites work "sometimes". Every Cloudflare free-plan site is affected, not only ours (Turath fails identically).
OONI also shows Zain (AS36998) resets on `188.114.96.6/.3` and `188.114.97.6/.3` (mastodon.cloud, wokala.vip, Oct 2026).

## Draft 1: Cloudflare Community (Network / DNS), posted by the user

> **Title:** 188.114.96.6 and 188.114.97.6 unreachable from Sudani (AS15706, Sudan), and DNS keeps returning them
>
> From Sudani mobile (AS15706), proxied hostnames on our zone (arthurarchive.com) and other zones (e.g. turath.io) often resolve to 188.114.96.6 / 188.114.97.6, including via 1.1.1.1.
> Both addresses time out on TCP 443 and drop 100% of ICMP from Sudani. The same addresses work from Starlink in the same place.
> Other Cloudflare addresses (188.114.96.3, 188.114.97.7, 104.21.84.124, 172.67.193.3) work from Sudani at the same moment.
> Result: Cloudflare-hosted sites randomly fail for Sudani users.
> Could you check reachability of those prefixes from AS15706, or avoid answering Sudanese resolvers with them? Happy to run traceroutes or other tests from the affected network.

## Draft 2: Turath developers (nuqayah), sent by the user

> السلام عليكم، وجدنا سبب تعذّر الوصول إلى تراث (وغيره من المواقع على Cloudflare) من شبكة سوداني:
> Cloudflare يعطي أحيانًا العنوانين 188.114.96.6 و188.114.97.6، وهما محجوبان أو غير قابلين للوصول على سوداني، بينما بقية عناوين Cloudflare تعمل.
> الحل الذي طبّقناه في تطبيقنا: نضيف إلى نتيجة DNS عناوين Cloudflare أخرى تعمل، ونفعّل الاتصال المتوازي (OkHttp 5 fast fallback).
> بهذا يتصل التطبيق خلال أقل من ثانية حتى حين يكون العنوان المعطى محجوبًا.

## Not yet done

- A traceroute from Sudani to 188.114.96.6, to see where packets die (Sudani's network, or upstream). Run on the phone with Wi-Fi off, via an app such as "PingTools", if Cloudflare asks for it.
- Run OONI Probe → Websites with our three hosts on Sudani, so the block is on public record.
