# Chapter 02 ��װKafka

### 2.1.3 ��װZookeeper

Zookeeper: 
* ��kafka�Ĺ�ϵ: ���𱣴漯Ⱥ��Ԫ������Ϣ(Metadata)����������Ϣ
![](kafka-and-zookeeper.jpg)
* ZookeeperȺ��:
    * ���������: ֻ�д����������ʱ, Zookeeper���ܴ����ⲿ����
    * ���������: ̫��: �ڵ�ʧЧ���մ�; ̫��: ��������(**һ����Э��**)
* ��������
    ```properties
    dataDir=/var/lib/zookeeper

    # ��λʱ��, ��λΪms
    tickTime=2000 
    # ���ӽ�㽨����ʼ��ʱ�������, 20*tickTime
    initLimit=20  
    # ���ӽ��ͬ��ʱ������, 5*tickTime
    syncLimit=5   

    # �ͻ��˿�
    clientPort=2181 

    server.1=zoo1.example.com:2888:3888
    server.2=zoo2.example.com:2888:3888
    server.3=zoo3.example.com:2888:3888
    # server.X=hostname:peerport:leaderport
        # X: ������ID, ����, ��������, ���ش�0��ʼ
        #     �����л�Ҫͨ��*myid�ļ�*ָ���Լ���ID
        # hostname: ������/IP
        # peerport: ���ڽڵ��ͨ��
        # leaderport: ��������ѡ��

    # �ڵ��ͨ��Ҫͬʱ�õ�3���˿�
    ```

## 2.3 broker����

����: 
* ��������:
    ```properties
    broker.id = 0 # broker�ļ�Ⱥ�е�Ψһ��ʶ��
        # �������ó�����������,����ӳ��

    # ��������
    port = 9092 

    zookeeper.connect = localhost:2181/path
    # hostname:port/path
        # hostname: Zookeer������
        # port: Zookeer�˿�
        # path: Zookeer·��(optional) 
            # Zookeeper��ͬʱ������Ӧ�ó���
            # ��ͬpath�ض���Ӧ�ó���

    # ??? 
    # Partition��־Ƭ�α���·��
    log.dirs = /kafkf/log 
        # ����ָ�����, ���ŷָ�
        # һ��Partition��Ƭ�ζ���һ��Ŀ¼��
        # ����ʹ��ԭ��: ѡ��������ĿPartition��·����������

    num.recovery.threads.per.data.dir = 2
    # Ĭ��: 1 (һ��Ŀ¼һ���߳�)
    # �̳߳ص��߳���, �߳�����
        # ����������ʱ, ������־Ƭ��
        # ����������������, �����ض̷���
        # �������ر�ʱ, ������־Ƭ��
        
    # �Զ�����Topic
    auto.create.topics.enable = false
    # �����Զ�����Topic������:
        # ������д��
        # ����������
        # �ͻ��������ⷢ��Ԫ��������
    ```
* Ĭ������:
    ```properties
    # ������
    num.partitions = 1
    # Ĭ��: 1
    # ֻ�����Ӳ��ܼ���
    # ����:
        # ������������
        # ������������, �������߶����һ��
        # broker����ķ�����, ���̿ռ�, ����
        # �������Key��������Ϣ, ��������ӷ���
        # broker�Է������������� (�������ڴ��)

    # ��־Ƭ��ɾ������
    log.retention.hours = 168
    # Ĭ��: 168, ��һ��
    # ����log.retention.ms, log.retention.minutes
    # ͨ����־Ƭ�ε�����޸�ʱ���뵱ǰʱ�����ж�
        # ����ı�������޸�ʱ��(���ƶ�), ��׼ȷ

    # ��־Ƭ�δ�С����, ������ɾ��
    log.retention.bytes = 1024
    # Ĭ��: 1 GB
    # ע��: �ǵ�������, �����ܺ�, ���Ƿ���Ƭ��

    # ͬʱ���� log.retention.hours/minutes/ms/bytesʱ
        # �������⼴��ɾ����Ϣ

    # ��������ʱ, ������־Ƭ���ļ���������Ƭ��
    log.segment.bytes = 1024
    # Ĭ��: 1 GB
    # ����:
        # ̫С: Ƶ�������ļ�����; ̫��: ϵͳ������ʧ������Ϣ
        # ��־Ƭ�α�����ǰ, Ƭ�β���������log.retention.*����ɾ��
        # ����offsetʱ, ���ȸ���ʱ�����λ��־Ƭ��, ���Ҿ���offset
            # ����־Ƭ��ԽС, ��λԽ׼ȷ

    # ��־Ƭ�ζ�ʱ��������
    log.segment.ms = 1000    
    # ͬʱ���� log.segment.ms/bytes, ������һ������
    # ����:
        # ��־Ƭ�δ�С���ù���ʱ
            # �����־Ƭ�εı�������ڵ���log.segment.ms����ʱͬʱ����
            #��ɶԴ�������Ӱ��

    # broker���ܵ������Ϣ����(ѹ����)
    message.max.bytes = 1024 
    # Ĭ��: 1 000 000
    # �����߷�����Ϣ����ʱ, broker���ش���
    # ����: 
        # �������²�����С��ƥ��:
            # �����ߵ�fetch.message.max.bytes
            # replica.fetch.max.bytes
    ```

## 2.4 Ӳ��ѡ��

Ӳ��ѡ��:
* ����������
    * Ӱ��: ��Ϣ�����ٶ�(��ϢҪ�־û���������)
* ���̴�С
    * Ӱ��: ������Ϣ����
* �ڴ��С:
    * Ӱ��: �����������ٶ�(�뻺���С�й�) ???
* ����
    * Ӱ��:
        * �������������
        * ��Ⱥ����
        * ����
* CPU
    * Ӱ��:
        * ѹ��

## 2.6 Kafka��Ⱥ

��Ⱥ�е�broker:
* broker����
    * ����:
        * ������: һ��Broker�ܱ���2GB, �򱣴�10GB����Ҫ5��Broker; ���replication-factorΪ2, ����Ҫ10��Broker
        * �������������
* **��Ⱥ��broker.id���ܳ�ͻ**
* һ����Ⱥ��һ��zookeeper������Э��