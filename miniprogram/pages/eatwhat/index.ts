const foods=["饺子","米线","盖饭","面条","轻食","麻辣烫"]; Page({data:{choice:"点一下，解决选择困难"},choose(){this.setData({choice:foods[Math.floor(Math.random()*foods.length)]})}});
