# CGB4 2023, Jack Heseltine: CoinCounter_ plugin

## Informal progress notes and results on coin value counting from image problem

### draft steps

* familiarize with color inspector
* template for plugin, test color inspector
* reading color vs 30 mm reference mark, settle on approach
* tests and refinement

### further documentation

In-code, as specified.

### image I/O (coins4 example: correctly classified)

![image](https://github.com/heseltime/CGB4-ImageJ/assets/66922223/5963aab9-aac7-4d83-8320-a865f72ab87c)
![image](https://github.com/heseltime/CGB4-ImageJ/assets/66922223/451149f4-7db9-414d-bf9c-0b2ebee307fb)
![image](https://github.com/heseltime/CGB4-ImageJ/assets/66922223/c2310f30-3fc6-4ed1-9b34-eb4ac50f7038)
![image](https://github.com/heseltime/CGB4-ImageJ/assets/66922223/f08d4e12-4e0e-44af-b556-1d4a15bad6d5)

### console I/O (coins4 example: correctly classified)

```
referenceScalingFactor (ANSWER to Task 1.3): 14.266666666666667
10 labels applied (ANSWER 1 to Task 2.3)
{0=1, 1=288, 2=314, 3=212, 4=212, 5=287, 6=246, 7=202, 8=320, 9=274, 10=277} diameters (in pixels) per label (ANSWER 2 to Task 2.3)
Final sum is 1 euro and 64 cents (1,64 EURO) (ANSWER to Task 3)
```

### image I/O (coins5 example: correctly classified)

![image](https://github.com/heseltime/CGB4-ImageJ/assets/66922223/11dd963c-f1f4-40fa-a3f4-2909715aa6f3)
![image](https://github.com/heseltime/CGB4-ImageJ/assets/66922223/f973225a-d5e2-4443-bada-ba3e52858b11)
![image](https://github.com/heseltime/CGB4-ImageJ/assets/66922223/62543b2d-2872-4f02-91ff-74d5b159d90a)


### console I/O (coins5 example: correctly classified)

```
referenceScalingFactor (ANSWER to 1.3): 12.4
14 labels applied (ANSWER 1 to Task 2.3)
{0=1, 1=265, 2=198, 3=292, 4=226, 5=252, 6=265, 7=201, 8=287, 9=262, 10=237, 11=163, 12=265, 13=195, 14=254} diameters (in pixels) per label (ANSWER 2 to Task 2.3)
Final sum is 1 euro and 91 cents (1,91 EURO) (ANSWER to Task 3) 
```

### sources for further reading

* https://imagej.nih.gov/ij/plugins/color-inspector.html
* https://home2.htw-berlin.de/~barthel/ImageJ/ColorInspector/help.htm
