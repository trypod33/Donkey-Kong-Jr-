import os, math
from PIL import Image, ImageDraw

RES = 'app/src/main/res/drawable-nodpi'
bm = {}
for f in os.listdir(RES):
    if f.endswith('.png'):
        im = Image.open(os.path.join(RES, f)).convert('RGBA')
        bm[f[:-4]] = im.resize((im.width//8, im.height//8), Image.NEAREST)

VW, VH = 224, 256
P = dict(
 ORANGE=(255,151,0,255), ORANGE_D=(184,71,0,255), VINE=(0,176,0,255), CHAIN=(242,156,20,255),
 PINK=(240,88,255,255), BLUE=(4,3,255,255), BLUE_L=(56,215,255,255), LOCK=(244,186,21,255),
 CYAN=(0,255,255,255), WHITE=(254,252,255,255), RED=(232,7,10,255), PURP=(118,115,255,255),
 GOLD=(246,218,24,255))

def lv1():
    L = dict(segs=[], vines=[], towers=[], elevs=[], tracks=[], fruits=[], keys=[], locks=[], enemies=[], spark=[])
    S = L['segs'].append; V = L['vines'].append
    S((0,223,41,1)); S((0,111,81,1)); S((128,223,81,1)); S((0,95,121,1)); S((112,207,121,1))
    S((16,111,161,1)); S((128,223,161,1)); S((0,95,201,1)); S((112,207,201,1)); S((0,223,241,1))
    for x, t, b in [(112,49,81),(124,49,81),(112,89,121),(208,89,121),(96,129,161),(112,129,161),
                    (208,129,161),(112,169,201),(96,169,201),(208,169,201),(96,209,241),(208,209,241),(48,209,241)]:
        V((x,t,b,1))
    L['keys'] = [(124,58),(112,98),(208,138),(96,178)]
    L['fruits'] = [(40,111,3),(180,71,1),(60,151,2),(150,191,0),(112,231,2)]
    L['enemies'] = [('jaw_red',177,41,1),('jaw_red',60,161,-1),('jaw_blue',114,65,1),
                    ('bird_nit',-10,101,1),('bird_chain',234,181,-1)]
    L['mario'] = (130,41); L['cage'] = (4,3); L['jr'] = (16,241)
    return L

def lv2():
    L = dict(segs=[], vines=[], towers=[], elevs=[], tracks=[], fruits=[], keys=[], locks=[], enemies=[], spark=[])
    S = L['segs'].append; V = L['vines'].append
    S((0,151,73,2)); S((168,191,73,2)); S((0,207,149,2)); S((32,48,161,2)); S((192,208,161,2)); S((0,223,241,1))
    L['towers'] = [(32,48,169,241),(192,208,169,241)]
    for x,t,b in [(36,169,245),(200,169,245)]: V((x,t,b,1))
    for i,x in enumerate([8,24,40,56,72,88,104,120,136]):
        V((x,81,145 if i%2==0 else 137,2))
    L['keys'] = [(66,62)]
    L['fruits'] = [(24,105,2),(120,97,3),(88,129,1),(36,139,0),(160,231,2)]
    L['enemies'] = [('jaw_red',110,149,1),('jaw_blue',74,113,1),('bird_nit',-10,110,1),('bird_chain',234,190,-1)]
    L['mario'] = (98,73); L['cage'] = (6,35); L['jr'] = (16,241)
    return L

def lv3():
    L = dict(segs=[], vines=[], towers=[], elevs=[], tracks=[], fruits=[], keys=[], locks=[], enemies=[], spark=[])
    S = L['segs'].append; V = L['vines'].append
    S((0,56,72,3)); S((88,180,72,3)); S((24,96,120,3)); S((112,208,120,3))
    S((0,48,160,3)); S((64,160,160,3)); S((176,208,160,3))
    S((16,96,200,3)); S((112,176,200,3)); S((0,223,240,3))
    L['elevs'] = [(8,96,240),(216,96,240)]
    for x,t,b in [(92,80,120),(28,128,160),(200,128,160),(40,168,200),(92,208,240)]: V((x,t,b,4))
    L['fruits'] = [(112,110,3),(40,150,1),(128,190,2),(64,230,0),(180,110,3)]
    L['enemies'] = [('spark_blue',140,120),('spark_blue',40,160),('bird_nit',80,140,1)]
    L['keys'] = [(140,74)]
    L['mario'] = (135,72); L['cage'] = (2,34); L['jr'] = (112,240)
    return L

def lv4():
    L = dict(segs=[], vines=[], towers=[], elevs=[], tracks=[], fruits=[], keys=[], locks=[], enemies=[], spark=[])
    S = L['segs'].append; V = L['vines'].append
    S((8,216,72,4)); S((0,223,240,4))
    for c in [28,76,100,124,148,196]:
        V((c-2,80,245,2)); L['locks'].append(c); L['keys'].append((c-7,229))
    L['fruits'] = [(56,100,2),(164,110,3),(40,230,1),(180,230,2)]
    L['enemies'] = [('bird_nit',60,130,1),('bird_chain',234,170,-1),('jaw_blue',100,150,1)]
    L['mario'] = (54,72); L['cage'] = (96,34); L['jr'] = (112,240)
    return L

def tile_row(d, seg, col):
    x0,x1,y,t = seg
    b = bm['gird%d'%t]
    step = b.width
    x = x0
    while x < x1:
        im.paste(b, (int(x), int(y)), b)
        x += step
    # clip right edge
    if x > x1:
        d.rectangle((int(x1), int(y), int(x)-1, int(y)+b.height-1), fill=(0,0,0,255))

def draw_stage(L, num):
    im = Image.new('RGBA', (VW, VH), (0,0,0,255))
    d = ImageDraw.Draw(im)
    # hud
    d.text((10,4), '1UP', fill=P['RED']); d.text((10,14), '000000', fill=P['WHITE'])
    d.text((76,4), 'HIGH SCORE', fill=P['CYAN']); d.text((84,14), '050000', fill=P['WHITE'])
    d.text((182,24), 'BONUS', fill=P['PINK'])
    d.rectangle((174,37,222,53), outline=P['WHITE']); d.text((184,39), '5000', fill=P['CYAN'])
    d.text((186,54), 'L=03', fill=P['WHITE'])
    # decor stage3
    if num == 3:
        d.rectangle((94,0,97,71), fill=P['BLUE'])
        for y in range(4,70,8): d.rectangle((95,y,96,y+1), fill=P['WHITE'])
        d.rectangle((56,88,103,91), fill=P['WHITE'])
        for x in range(60,100,8): d.rectangle((x,87,x+3,92), fill=P['PURP'])
    for t in L['tracks']:
        x,t_,b = t
        for y in range(int(t_), int(b), 6): d.rectangle((int(x), y, int(x)+1, y+1), fill=P['CYAN'])
    for tw in L['towers']:
        b = bm['brick']
        for x in range(int(tw[0]), int(tw[1]), 8):
            for y in range(int(tw[2]), int(tw[3]), 8): im.paste(b, (x,y), b)
        cap = bm['cap1']; im.paste(cap, (int(tw[0]), int(tw[2])-8), cap)
    for seg in L['segs']: tile_row(d, seg, None)
    for v in L['vines']:
        x,t_,b,kind = v
        if kind == 1:
            sp = bm['vine']
            for y in range(int(t_), int(b), sp.height): im.paste(sp, (int(x)-2, y), sp)
        elif kind == 2:
            sp = bm['chain']
            for y in range(int(t_), int(b), sp.height): im.paste(sp, (int(x)-2, y), sp)
    for i,e in enumerate(L['elevs']):
        x,t_,b = e
        d.rectangle((x-8,t_-8,x-7,b), fill=P['PURP']); d.rectangle((x+7,t_-8,x+8,b), fill=P['PURP'])
        cap = bm['elevcap']; pos = b - 20 - i*30
        im.paste(cap, (int(x)-8, int(pos)), cap)
    for cx in L['locks']:
        d.rectangle((cx-8,80,cx+8,95), fill=P['LOCK'])
        d.rectangle((cx-3,86,cx+3,92), fill=(0,0,0,255))
        d.rectangle((cx-1,89,cx+1,93), fill=(0,0,0,255))
    cage = bm['cage']; cx, cy = L['cage']
    im.paste(cage, (int(cx), int(cy)), cage)
    fnames = ['fruit_cherry','fruit_orange','fruit_banana']
    for f in L['fruits']:
        x,y,k = f
        sp = bm[fnames[k] if k < 3 else fnames[0]]
        im.paste(sp, (int(x)-sp.width//2, int(y)), sp)
    knames = {1:'key_blue',2:'key_pink',3:'key_white',4:'key_blue'}
    for k in L['keys']:
        sp = bm[knames[num]]
        im.paste(sp, (int(k[0]), int(k[1])), sp)
    for e in L['enemies']:
        name = e[0]
        if name in ('bird_nit','bird_chain'):
            x, y = e[1], e[2]
            sp = bm[name]; im.paste(sp, (int(x)-8, int(y)-6), sp)
        elif name == 'spark_blue':
            x, y = e[1], e[2]
            sp = bm[name]; im.paste(sp, (int(x)-7, int(y)-6), sp)
        else:
            x, y, dr = e[1], e[2], e[3]
            sp = bm[name]
            if dr < 0: sp = sp.transpose(Image.FLIP_LEFT_RIGHT)
            im.paste(sp, (int(x)-7, int(y)-4), sp)
    mx, my = L['mario']
    sp = bm['mario']; im.paste(sp, (int(mx)-8, int(my)-16), sp)
    jx, jy = L['jr']
    sp = bm['jr_idle']; im.paste(sp, (int(jx)-7, int(jy)-16), sp)
    d.text((82,120), 'STAGE %d'%num, fill=P['PINK'])
    d.text((78,136), 'GET READY!', fill=P['WHITE'])
    return im

sheet = Image.new('RGBA', (224*4+40, 256+10), (20,20,20,255))
for i, fn in enumerate([lv1,lv2,lv3,lv4]):
    im = draw_stage(fn(), i+1)
    sheet.paste(im, (i*(224+10)+5, 5))
sheet = sheet.resize((sheet.width*2, sheet.height*2), Image.NEAREST)
sheet.save('refs/preview_stages.png')
print('saved', sheet.size)
